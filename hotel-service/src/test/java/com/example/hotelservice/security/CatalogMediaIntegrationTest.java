package com.example.hotelservice.security;

import com.example.hotelservice.grpc.*;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class CatalogMediaIntegrationTest {
 @org.springframework.boot.test.context.TestConfiguration
 @EnableJpaRepositories(basePackageClasses={HotelRepository.class,RoomTypeRepository.class,MediaAssetRepository.class})
 static class Config {
  @Bean DriverManagerDataSource dataSource(){String url=System.getenv("CHECKINN_RANGE_DB_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");return new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource source){
   var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.example.hotelservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
   factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.generate_statistics","true","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;
  }
  @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory){return new JpaTransactionManager(factory);}
  @Bean JdbcTemplate jdbc(DriverManagerDataSource source){return new JdbcTemplate(source);}
  @Bean HotelGrpcServiceImpl rpc(HotelRepository hotels,RoomTypeRepository rooms,MediaAssetRepository media){
   var roomService=new com.example.hotelservice.Room.service.RoomTypeServiceImpl(rooms,hotels,new com.fasterxml.jackson.databind.ObjectMapper(),mock(com.example.hotelservice.Room.service.RoomInventoryReviewService.class));
   var mediaService=new com.example.hotelservice.MediaAsset.service.MediaServiceImpl(media,mock(com.example.hotelservice.MediaAsset.service.MediaClient.class),hotels,rooms,new com.example.hotelservice.MediaAsset.service.MediaMutationService(media,hotels,rooms));
   return new HotelGrpcServiceImpl(mock(com.example.hotelservice.Hotel.service.HotelService.class),roomService,mediaService,new com.fasterxml.jackson.databind.ObjectMapper());
  }
 }
 static class Fixture implements AutoCloseable {
  final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class);
  final JdbcTemplate db=context.getBean(JdbcTemplate.class);final HotelGrpcServiceImpl rpc=context.getBean(HotelGrpcServiceImpl.class);
  final UUID hotel=UUID.randomUUID(),owner=UUID.randomUUID();final List<UUID> rooms=new ArrayList<>();
  final org.hibernate.stat.Statistics stats=context.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();
  Fixture(int count){
   assertThat(db.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");
   assertThat(db.update("INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT ?,?,h.city_id,'Synthetic catalog RPC hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'APPROVED',true,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1",hotel,owner)).isEqualTo(1);
   for(int i=0;i<=count;i++){
    UUID room=UUID.randomUUID();rooms.add(room);boolean active=i<count;
    db.update("INSERT INTO room_types(id,hotel_id,name,base_price,capacity,amenities,is_active,total_rooms,created_at,updated_at) VALUES (?,?,'Synthetic catalog room',125000.50,'{\"adults\":2,\"children\":0}'::jsonb,'null'::jsonb,?,3,now(),now())",room,hotel,active);
    db.update("INSERT INTO media_assets(id,target_id,target_type,url,is_thumbnail,sort_order,created_at,updated_at) VALUES (?,?,'ROOM_TYPE',?,false,0,now(),now())",UUID.randomUUID(),room,"https://example.invalid/"+room+".jpg");
   }
  }
  Capture read(){var result=new Capture();rpc.getRoomTypesByHotel(GetRoomTypesByHotelRequest.newBuilder().setHotelId(hotel.toString()).build(),result);return result;}
  @Override public void close(){try{for(UUID room:rooms){db.update("DELETE FROM media_assets WHERE target_id=? AND target_type='ROOM_TYPE'",room);db.update("DELETE FROM room_types WHERE id=? AND hotel_id=?",room,hotel);}db.update("DELETE FROM hotels WHERE id=? AND owner_id=?",hotel,owner);}finally{context.close();}}
 }
 static class Capture implements io.grpc.stub.StreamObserver<GetRoomTypesByHotelResponse>{GetRoomTypesByHotelResponse value;Throwable error;boolean completed;
  public void onNext(GetRoomTypesByHotelResponse next){value=next;}public void onError(Throwable failure){error=failure;}public void onCompleted(){completed=true;}}
 @ParameterizedTest @ValueSource(ints={1,12})
 void roomImagesUseConstantQueriesAndNeverIncludeInactiveRooms(int count){
  try(var f=new Fixture(count)){
   // Use [] for this query-count regression; JSON-null compatibility has its own test.
   f.db.update("UPDATE room_types SET amenities='[]'::jsonb WHERE hotel_id=?",f.hotel);
   f.stats.clear();var result=f.read();assertThat(result.error).isNull();assertThat(result.completed).isTrue();assertThat(result.value.getRoomTypesCount()).isEqualTo(count);
   assertThat(result.value.getRoomTypesList()).allSatisfy(room->{assertThat(room.getImagesCount()).isEqualTo(1);assertThat(room.getImages(0).getUrl()).endsWith(room.getId()+".jpg");assertThat(room.getHotelId()).isEqualTo(f.hotel.toString());});
   assertThat(f.stats.getPrepareStatementCount()).as("SQL count for %s published rooms",count).isLessThanOrEqualTo(3);
  }
 }
 @Test void unpublishedHotelStillFailsBeforeReturningAnyRoomOrImage(){
  try(var f=new Fixture(1)){
   f.db.update("UPDATE hotels SET approved_status='DRAFT' WHERE id=?",f.hotel);var result=f.read();
   assertThat(f.context.getBean(MediaAssetRepository.class).findPublicRoomMediaByHotel(f.hotel,com.example.hotelservice.MediaAsset.enums.MediaTargetType.ROOM_TYPE,com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED)).isEmpty();
   assertThat(result.value).isNull();assertThat(io.grpc.Status.fromThrowable(result.error).getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
  }
 }
 @Test void imagesOfAnotherPublishedHotelCannotEnterTheResponse(){
  try(var own=new Fixture(1);var foreign=new Fixture(1)){
   var result=own.read();assertThat(result.error).isNull();assertThat(result.value.getRoomTypesCount()).isEqualTo(1);
   var room=result.value.getRoomTypes(0);assertThat(room.getHotelId()).isEqualTo(own.hotel.toString());assertThat(room.getImages(0).getUrl()).endsWith(room.getId()+".jpg");
  }
 }

}
