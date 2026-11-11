package com.example.hotelservice.security;

import com.example.hotelservice.MediaAsset.service.*;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.Hotel.repository.*;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class MediaMetadataIntegrationTest {
 static class Gate {
  volatile boolean failReorder,storageAck=true;
  final CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1),provider=new CountDownLatch(1),uploaded=new CountDownLatch(1),deleteRead=new CountDownLatch(1);
 }
 @org.springframework.boot.test.context.TestConfiguration
 @EnableTransactionManagement(proxyTargetClass=true)
 @EnableJpaRepositories(basePackageClasses={HotelRepository.class,RoomTypeRepository.class,MediaAssetRepository.class})
 static class Config {
  @Bean DriverManagerDataSource dataSource(){String url=System.getenv("CHECKINN_RANGE_DB_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");return new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource source){var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.example.hotelservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;}
  @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory,DriverManagerDataSource source){var tx=new JpaTransactionManager(factory);tx.setDataSource(source);return tx;}
  @Bean JdbcTemplate jdbc(DriverManagerDataSource source){return new JdbcTemplate(source);}
  @Bean Gate gate(){return new Gate();}
  @Bean @Primary MediaAssetRepository guarded(@Qualifier("mediaAssetRepository") MediaAssetRepository real,Gate gate){return mock(MediaAssetRepository.class,call->{
   String method=call.getMethod().getName(),thread=Thread.currentThread().getName();
   if(method.equals("saveAll")&&gate.failReorder)throw new org.springframework.dao.DataAccessResourceFailureException("Synthetic reorder failure");
   if((method.equals("saveAll")&&thread.equals("select-race"))||((method.equals("save")||method.equals("saveAndFlush"))&&thread.equals("register-delete-race"))){gate.held.countDown();if(!gate.release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timed out");}
   if(method.equals("findByTargetIdAndTargetType")&&thread.equals("draft-create-race")){gate.held.countDown();if(!gate.release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timed out");}
   Object result;try{result=call.getMethod().invoke(real,call.getArguments());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   if((method.equals("save")||method.equals("saveAndFlush"))&&thread.equals("upload-race"))gate.uploaded.countDown();
   if(method.equals("findByTargetIdAndTargetType")&&thread.equals("draft-delete-race"))gate.deleteRead.countDown();
   return result;
  });}
  @Bean com.example.hotelservice.Room.service.RoomTypeServiceImpl roomService(RoomTypeRepository rooms,HotelRepository hotels){return new com.example.hotelservice.Room.service.RoomTypeServiceImpl(rooms,hotels,new com.fasterxml.jackson.databind.ObjectMapper(),mock(com.example.hotelservice.Room.service.RoomInventoryReviewService.class));}
  @Bean MediaClient storage(Gate gate){var storage=mock(MediaClient.class);when(storage.upload(any(),any(),any())).thenAnswer(call->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();gate.provider.countDown();return "https://example.invalid/synthetic-upload.png";});when(storage.deleteByUrl(any())).thenAnswer(call->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return gate.storageAck;});return storage;}
  @Bean MediaMutationService mutations(MediaAssetRepository assets,HotelRepository hotels,RoomTypeRepository rooms){return new MediaMutationService(assets,hotels,rooms);}
  @Bean MediaServiceImpl media(MediaAssetRepository assets,MediaClient storage,HotelRepository hotels,RoomTypeRepository rooms,MediaMutationService mutations){return new MediaServiceImpl(assets,storage,hotels,rooms,mutations);}
  @Bean com.example.hotelservice.Hotel.service.HotelServiceImpl hotelService(HotelRepository hotels,MediaAssetRepository assets,MediaClient storage){return new com.example.hotelservice.Hotel.service.HotelServiceImpl(new com.example.hotelservice.Hotel.service.AccommodationTypeService(hotels,mock(AccommodationTypeChangeRepository.class)),mock(com.example.hotelservice.Hotel.service.UserGrpcClient.class),storage,hotels,mock(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class),mock(com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository.class),mock(com.example.hotelservice.Question.repository.QuestionRepository.class),mock(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class),mock(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class),mock(com.example.hotelservice.City.repository.CityRepository.class),new com.fasterxml.jackson.databind.ObjectMapper(),mock(com.example.hotelservice.Hotel.mapper.HotelMapper.class),assets);}
 }
 static class Fixture implements AutoCloseable {
  final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class);final JdbcTemplate db=context.getBean(JdbcTemplate.class);final MediaServiceImpl service=context.getBean(MediaServiceImpl.class);final Gate gate=context.getBean(Gate.class);
  final UUID hotel=UUID.randomUUID(),owner=UUID.randomUUID(),room=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();final MediaTargetType type;final UUID target;
  Fixture(boolean roomTarget){type=roomTarget?MediaTargetType.ROOM_TYPE:MediaTargetType.HOTEL;target=roomTarget?room:hotel;
   assertThat(db.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");
   assertThat(db.update("INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT ?,?,h.city_id,'Synthetic metadata hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1",hotel,owner)).isEqualTo(1);
   if(roomTarget)db.update("INSERT INTO room_types(id,hotel_id,name,base_price,capacity,amenities,is_active,total_rooms,created_at,updated_at) VALUES (?,?,'Synthetic metadata room',125000,'{\"adults\":2,\"children\":0}'::jsonb,'[]'::jsonb,true,3,now(),now())",room,hotel);
   for(int i=0;i<2;i++)db.update("INSERT INTO media_assets(id,target_id,target_type,url,is_thumbnail,sort_order,created_at,updated_at) VALUES (?,?,?, ?,?,?,now(),now())",i==0?a:b,target,type.name(),"https://example.invalid/"+(i==0?a:b)+".png",i==0,i);
  }
  <T>T owner(Supplier<T> call){var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic").header("alg","HS256").subject(owner.toString()).claim("role","OWNER").build()));SecurityContextHolder.setContext(ctx);try{return call.get();}finally{SecurityContextHolder.clearContext();}}
  com.example.hotelservice.MediaAsset.entity.MediaAsset upload(){try{return service.uploadMultipart(target,type,true,2,new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",new byte[]{1,2}));}catch(Exception failure){throw new RuntimeException(failure);}}
  long thumbnails(){return db.queryForObject("SELECT count(*) FROM media_assets WHERE target_id=? AND target_type=? AND is_thumbnail",Long.class,target,type.name());}
  @Override public void close(){gate.release.countDown();try{db.update("DELETE FROM media_assets WHERE (target_id=? AND target_type='HOTEL') OR (target_id=? AND target_type='ROOM_TYPE')",hotel,room);db.update("DELETE FROM room_types WHERE id=? AND hotel_id=?",room,hotel);db.update("DELETE FROM hotels WHERE id=? AND owner_id=?",hotel,owner);}finally{context.close();}}
 }
 @ParameterizedTest @ValueSource(booleans={false,true}) void thumbnailAcknowledgementMatchesCommittedFlag(boolean room){try(var f=new Fixture(room)){var ack=f.owner(()->f.service.updateThumbnail(f.b));assertThat(ack.getIsThumbnail()).isTrue();assertThat(f.thumbnails()).isEqualTo(1);assertThat(f.db.queryForObject("SELECT is_thumbnail FROM media_assets WHERE id=?",Boolean.class,f.b)).isTrue();}}
 @ParameterizedTest @ValueSource(booleans={false,true}) void uploadedThumbnailReplacesPreviousFlag(boolean room){try(var f=new Fixture(room)){var ack=f.owner(f::upload);assertThat(ack.getIsThumbnail()).isTrue();assertThat(f.thumbnails()).isEqualTo(1);}}
 @Test void deleteAndReorderRollBackTogether(){try(var f=new Fixture(false)){f.gate.failReorder=true;assertThatThrownBy(()->f.owner(()->{f.service.delete(f.a);return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(f.db.queryForObject("SELECT count(*) FROM media_assets WHERE id=?",Long.class,f.a)).isEqualTo(1);assertThat(f.db.queryForObject("SELECT sort_order FROM media_assets WHERE id=?",Integer.class,f.b)).isEqualTo(1);}}
 @Test void selectionAndUploadShareOneTargetLock() throws Exception {try(var f=new Fixture(false)){var pool=Executors.newFixedThreadPool(2);try{
  var select=pool.submit(()->{Thread.currentThread().setName("select-race");return f.owner(()->f.service.updateThumbnail(f.b));});assertThat(f.gate.held.await(10,TimeUnit.SECONDS)).isTrue();
  var upload=pool.submit(()->{Thread.currentThread().setName("upload-race");return f.owner(f::upload);});assertThat(f.gate.provider.await(10,TimeUnit.SECONDS)).isTrue();f.gate.uploaded.await(1,TimeUnit.SECONDS);f.gate.release.countDown();select.get(15,TimeUnit.SECONDS);upload.get(15,TimeUnit.SECONDS);assertThat(f.thumbnails()).isEqualTo(1);
 }finally{f.gate.release.countDown();pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);}}}
 @Test void draftDeletionCannotLeaveConcurrentNewMetadata() throws Exception {try(var f=new Fixture(false)){var pool=Executors.newFixedThreadPool(2);try{
  var upload=pool.submit(()->{Thread.currentThread().setName("register-delete-race");return f.owner(f::upload);});assertThat(f.gate.held.await(10,TimeUnit.SECONDS)).isTrue();
  var remove=pool.submit(()->{Thread.currentThread().setName("draft-delete-race");f.context.getBean(com.example.hotelservice.Hotel.service.HotelServiceImpl.class).deleteDraftHotel(f.hotel,f.owner);return null;});f.gate.deleteRead.await(1,TimeUnit.SECONDS);f.gate.release.countDown();upload.get(15,TimeUnit.SECONDS);remove.get(15,TimeUnit.SECONDS);
  assertThat(f.db.queryForObject("SELECT count(*) FROM media_assets WHERE target_id=? AND target_type='HOTEL'",Long.class,f.hotel)).isZero();
 }finally{f.gate.release.countDown();pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);}}}
 @Test void roomCreationWaitsForDraftDeletion() throws Exception {try(var f=new Fixture(false)){
  var pool=Executors.newFixedThreadPool(2);try{
   var remove=pool.submit(()->{Thread.currentThread().setName("draft-create-race");f.context.getBean(com.example.hotelservice.Hotel.service.HotelServiceImpl.class).deleteDraftHotel(f.hotel,f.owner);return null;});
   assertThat(f.gate.held.await(10,TimeUnit.SECONDS)).isTrue();
   var started=new CountDownLatch(1);
   var create=pool.submit(()->{started.countDown();return f.context.getBean(com.example.hotelservice.Room.service.RoomTypeServiceImpl.class).createRoomType(new com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest(f.hotel,"Synthetic race room",java.math.BigDecimal.valueOf(125000),new com.example.hotelservice.Room.dto.request.CapacityDto(2,0,null,null,null,null),1,List.of()),f.owner);});
   assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
   assertThatThrownBy(()->create.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
   f.gate.release.countDown();remove.get(15,TimeUnit.SECONDS);
   assertThatThrownBy(()->create.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(jakarta.persistence.EntityNotFoundException.class);
   assertThat(f.db.queryForObject("SELECT count(*) FROM room_types WHERE hotel_id=?",Long.class,f.hotel)).isZero();
  }finally{f.gate.release.countDown();pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);f.db.update("DELETE FROM room_types WHERE hotel_id=?",f.hotel);}
 }}
 @Test void draftWithRoomsCannotBeDeleted(){try(var f=new Fixture(true)){
  assertThatThrownBy(()->f.context.getBean(com.example.hotelservice.Hotel.service.HotelServiceImpl.class).deleteDraftHotel(f.hotel,f.owner)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(f.db.queryForObject("SELECT count(*) FROM hotels WHERE id=?",Long.class,f.hotel)).isEqualTo(1);
  assertThat(f.db.queryForObject("SELECT count(*) FROM room_types WHERE id=?",Long.class,f.room)).isEqualTo(1);
  assertThat(f.db.queryForObject("SELECT count(*) FROM media_assets WHERE target_id=?",Long.class,f.room)).isEqualTo(2);
 }}
 @Test void providerRejectionDoesNotDeleteMetadata(){try(var f=new Fixture(false)){f.gate.storageAck=false;assertThatThrownBy(()->f.owner(()->{f.service.delete(f.a);return null;})).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(f.db.queryForObject("SELECT count(*) FROM media_assets WHERE id=?",Long.class,f.a)).isEqualTo(1);}}
}
