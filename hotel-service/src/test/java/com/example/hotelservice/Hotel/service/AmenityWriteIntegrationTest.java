package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Amenity.dto.request.*;
import com.example.hotelservice.Amenity.repository.*;
import com.example.hotelservice.Amenity.service.AmenityCatalogService;
import com.example.hotelservice.Hotel.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class AmenityWriteIntegrationTest {
 @org.springframework.boot.test.context.TestConfiguration
 @EnableTransactionManagement(proxyTargetClass=true)
 @EnableJpaRepositories(basePackageClasses={HotelRepository.class,HotelAmenityRepository.class})
 static class Config {
  @Bean DriverManagerDataSource dataSource(){String url=System.getenv("CHECKINN_RANGE_DB_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");return new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource source){var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.example.hotelservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;}
  @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory,DriverManagerDataSource source){var tx=new JpaTransactionManager(factory);tx.setDataSource(source);return tx;}
  @Bean JdbcTemplate jdbc(DriverManagerDataSource source){return new JdbcTemplate(source);}
  @Bean AmenityCatalogService catalog(JdbcTemplate db,HotelRepository hotels){return new AmenityCatalogService(db,hotels);}
  @Bean HotelServiceImpl service(HotelRepository hotels,HotelAmenityCategoryRepository categories,HotelAmenityRepository items){
   var guarded=mock(HotelAmenityRepository.class,call->{
    if(call.getMethod().getName().equals("save")){
     String title=call.getArgument(0,com.example.hotelservice.Amenity.entity.HotelAmenity.class).getTitle();
     if(title.equals("Synthetic forced rollback"))throw new org.springframework.dao.DataIntegrityViolationException("Synthetic write failure");
     if(title.startsWith("Concurrent"))Thread.sleep(100);
    }
    try{return call.getMethod().invoke(items,call.getArguments());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   return new HotelServiceImpl(new AccommodationTypeService(hotels,mock(AccommodationTypeChangeRepository.class)),mock(UserGrpcClient.class),mock(com.example.hotelservice.MediaAsset.service.MediaClient.class),hotels,
    mock(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class),mock(com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository.class),mock(com.example.hotelservice.Question.repository.QuestionRepository.class),
    categories,guarded,mock(com.example.hotelservice.City.repository.CityRepository.class),new com.fasterxml.jackson.databind.ObjectMapper(),mock(com.example.hotelservice.Hotel.mapper.HotelMapper.class),mock(com.example.hotelservice.MediaAsset.repository.MediaAssetRepository.class));
  }
 }
 static AmenityRequest category(String title,String... names){var value=new AmenityRequest();value.setTitle(title);var items=new ArrayList<AmenityItemRequest>();for(String name:names){var item=new AmenityItemRequest();item.setTitle(name);items.add(item);}value.setAmenities(items);return value;}
 static class Fixture implements AutoCloseable {
  final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class);
  final JdbcTemplate db=context.getBean(JdbcTemplate.class);final HotelServiceImpl service=context.getBean(HotelServiceImpl.class);final AmenityCatalogService catalog=context.getBean(AmenityCatalogService.class);
  final UUID hotel=UUID.randomUUID(),owner=UUID.randomUUID(),admin=UUID.randomUUID();final String code="SYNTHETIC_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
  Fixture(){assertThat(db.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");assertThat(db.update("INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT ?,?,h.city_id,'Synthetic amenity write hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1",hotel,owner)).isEqualTo(1);}
  List<String> titles(){return db.queryForList("SELECT a.title FROM hotel_amenities a JOIN hotel_amenity_categories c ON c.id=a.category_id WHERE c.hotel_id=? ORDER BY a.title",String.class,hotel);}
  UUID rawId(){return db.queryForObject("SELECT a.id FROM hotel_amenities a JOIN hotel_amenity_categories c ON c.id=a.category_id WHERE c.hotel_id=? LIMIT 1",UUID.class,hotel);}
  void seed(){service.updateAmenities(hotel,List.of(category("Synthetic category","Synthetic original")),owner);}
  @Override public void close(){try{
   db.update("DELETE FROM amenity_mapping_states WHERE hotel_id=?",hotel);db.update("DELETE FROM amenity_mapping_request_codes WHERE request_id IN (SELECT id FROM amenity_mapping_requests WHERE hotel_id=?)",hotel);db.update("DELETE FROM amenity_mapping_requests WHERE hotel_id=?",hotel);db.update("DELETE FROM amenity_catalog WHERE code=?",code);
   db.update("DELETE FROM hotel_amenities WHERE category_id IN (SELECT id FROM hotel_amenity_categories WHERE hotel_id=?)",hotel);db.update("DELETE FROM hotel_amenity_categories WHERE hotel_id=?",hotel);db.update("DELETE FROM hotels WHERE id=? AND owner_id=?",hotel,owner);
  }finally{context.close();}}
 }
 @Test void nullReplacementDoesNotClearExistingAmenities(){try(var f=new Fixture()){f.seed();assertThatThrownBy(()->f.service.updateAmenities(f.hotel,null,f.owner)).isInstanceOf(IllegalArgumentException.class);assertThat(f.titles()).containsExactly("Synthetic original");}}
 @Test void embeddedUpdateRoundTripsAndDistinguishesOmissionFromClear(){try(var f=new Fixture()){
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var input=mapper.convertValue(Map.of("amenityCategories",List.of(category("Synthetic category","Embedded synthetic"))),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class);
  f.service.updateHotel(f.hotel,input,f.owner);assertThat(f.titles()).containsExactly("Embedded synthetic");
  f.service.updateHotel(f.hotel,mapper.convertValue(Map.of(),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class),f.owner);assertThat(f.titles()).containsExactly("Embedded synthetic");
  f.service.updateHotel(f.hotel,mapper.convertValue(Map.of("amenityCategories",List.of()),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class),f.owner);assertThat(f.titles()).isEmpty();
 }}
 @Test void invalidNestedUpdateDoesNotChangeHotelScalars(){try(var f=new Fixture()){
  var input=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(Map.of("name","Synthetic changed name","amenityCategories",List.of(category(" ","Synthetic item"))),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class);
  assertThatThrownBy(()->f.service.updateHotel(f.hotel,input,f.owner)).isInstanceOf(IllegalArgumentException.class);assertThat(f.db.queryForObject("SELECT name FROM hotels WHERE id=?",String.class,f.hotel)).isEqualTo("Synthetic amenity write hotel");
 }}
 @Test void wholeReplacementRetainsAuditButNeverAutoApprovesSameText(){try(var f=new Fixture()){
  f.seed();UUID old=f.rawId();var admin=new AmenityCatalogService.Actor(f.admin,"ADMIN");
  f.catalog.save(new AmenityCatalogService.CatalogInput(f.code,"Synthetic catalog entry",null,true,0L),true,admin);
  var proposal=f.catalog.propose(f.hotel,old,new AmenityCatalogService.Proposal(Set.of(f.code),0L,"Synthetic proposal"),new AmenityCatalogService.Actor(f.owner,"OWNER"));
  UUID request=(UUID)proposal.get("id");f.catalog.review(request,new AmenityCatalogService.Review(true,((Number)proposal.get("revision")).longValue(),"Synthetic review"),admin);
  assertThat(f.catalog.effectiveCodes(f.hotel)).containsKey(old);
  f.seed();assertThat(f.rawId()).isNotEqualTo(old);assertThat(f.catalog.effectiveCodes(f.hotel)).isEmpty();
  assertThat(f.db.queryForObject("SELECT status FROM amenity_mapping_requests WHERE id=?",String.class,request)).isEqualTo("APPROVED");
 }}
 @Test void failureAfterDeleteAndFirstInsertRollsBackReplacement(){try(var f=new Fixture()){
  f.seed();assertThatThrownBy(()->f.service.updateAmenities(f.hotel,List.of(category("Synthetic new","Synthetic first","Synthetic forced rollback")),f.owner)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(f.titles()).containsExactly("Synthetic original");
 }}
 @Test void concurrentReplacementsAlreadySerializeOnTheHotelLock() throws Exception {try(var f=new Fixture()){
  var pool=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();try{
   for(int i=0;i<8;i++){final int batch=i;tasks.add(pool.submit(()->{gate.await();f.service.updateAmenities(f.hotel,List.of(category("Synthetic category","Concurrent "+batch+" A","Concurrent "+batch+" B")),f.owner);return null;}));}
   gate.countDown();for(var task:tasks)task.get(60,TimeUnit.SECONDS);assertThat(f.titles()).hasSize(2);assertThat(f.titles().stream().map(title->title.substring(0,title.length()-2)).distinct()).hasSize(1);
  }finally{pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);}
 }}
 @Test void anotherOwnerCannotClearAmenities(){try(var f=new Fixture()){f.seed();assertThatThrownBy(()->f.service.clearAmenities(f.hotel,UUID.randomUUID())).isInstanceOf(SecurityException.class);assertThat(f.titles()).containsExactly("Synthetic original");}}
}
