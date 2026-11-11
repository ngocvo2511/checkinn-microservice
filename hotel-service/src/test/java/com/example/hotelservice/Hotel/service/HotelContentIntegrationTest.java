package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.repository.*;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Policy.repository.*;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Question.repository.QuestionRepository;
import com.example.hotelservice.Question.dto.QuestionRequest;
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
class HotelContentIntegrationTest {
 @org.springframework.boot.test.context.TestConfiguration
 @EnableTransactionManagement(proxyTargetClass=true)
 @EnableJpaRepositories(basePackageClasses={HotelRepository.class,QuestionRepository.class,HotelPolicyRepository.class})
 static class Config {
  @Bean DriverManagerDataSource dataSource(){
   String url=System.getenv("CHECKINN_RANGE_DB_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");
   return new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
  }
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource source){
   var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.example.hotelservice");
   factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none",
    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;
  }
  @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory,DriverManagerDataSource source){var tx=new JpaTransactionManager(factory);tx.setDataSource(source);return tx;}
  @Bean JdbcTemplate jdbc(DriverManagerDataSource source){return new JdbcTemplate(source);}
  @Bean HotelServiceImpl service(HotelRepository hotels,QuestionRepository questions,HotelPolicyRepository policies,PolicyCategoryTypeRepository categories){
   // Widen the old delete-before-insert race; persistence and transactions remain real.
   var slowQuestions=mock(QuestionRepository.class,call->{
    if(call.getMethod().getName().equals("save")){
     String text=call.getArgument(0,com.example.hotelservice.Question.entity.Question.class).getQuestion();
     if(text.equals("Synthetic forced rollback"))throw new org.springframework.dao.DataIntegrityViolationException("Synthetic write fault");
     if(text.startsWith("Concurrent"))Thread.sleep(300);
    }
    try{return call.getMethod().invoke(questions,call.getArguments());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   var slowPolicies=mock(HotelPolicyRepository.class,call->{
    if(call.getMethod().getName().equals("save")&&call.getArgument(0,com.example.hotelservice.Policy.entity.HotelPolicy.class).getContent().startsWith("Concurrent"))Thread.sleep(300);
    try{return call.getMethod().invoke(policies,call.getArguments());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   return new HotelServiceImpl(new AccommodationTypeService(hotels,mock(AccommodationTypeChangeRepository.class)),
    mock(UserGrpcClient.class),mock(com.example.hotelservice.MediaAsset.service.MediaClient.class),hotels,slowPolicies,categories,slowQuestions,
    mock(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class),mock(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class),
    mock(com.example.hotelservice.City.repository.CityRepository.class),new com.fasterxml.jackson.databind.ObjectMapper(),mock(HotelMapper.class),mock(com.example.hotelservice.MediaAsset.repository.MediaAssetRepository.class));
  }
 }
 static QuestionRequest question(String text){var q=new QuestionRequest();q.setQuestion(text);q.setAnswer("Synthetic answer");return q;}
 static PolicyRequest policy(String category,String text){var p=new PolicyRequest();p.setCategory(category);p.setContent(text);return p;}
 static class Fixture implements AutoCloseable {
  final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class);
  final JdbcTemplate db=context.getBean(JdbcTemplate.class);final HotelServiceImpl service=context.getBean(HotelServiceImpl.class);
  final UUID hotel=UUID.randomUUID(),owner=UUID.randomUUID(),category=UUID.randomUUID();final String categoryName="Synthetic content "+category;
  Fixture(){
   assertThat(db.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");
   assertThat(db.update("INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT ?::uuid,?::uuid,h.city_id,'Synthetic content integrity hotel','{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\",\"coordinateSource\":\"UNKNOWN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1",hotel.toString(),owner.toString())).isEqualTo(1);
   db.update("INSERT INTO policy_category_types(id,name) VALUES (?,?)",category,categoryName);
  }
  List<String> questions(){return db.queryForList("SELECT question FROM hotel_questions WHERE hotel_id=?",String.class,hotel);}
  List<String> policies(){return db.queryForList("SELECT content FROM hotel_policies WHERE hotel_id=?",String.class,hotel);}
  @Override public void close(){try{db.update("DELETE FROM hotel_questions WHERE hotel_id=?",hotel);db.update("DELETE FROM hotel_policies WHERE hotel_id=?",hotel);
   db.update("DELETE FROM hotels WHERE id=? AND owner_id=?",hotel,owner);db.update("DELETE FROM policy_category_types WHERE id=?",category);
  }finally{context.close();}}
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void competingWholeListReplacementsCannotMergeIntoAListThatNobodySubmitted(boolean policies) throws Exception {
  try(var f=new Fixture()){
   var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();
   try{
    for(int i=0;i<8;i++){final int batch=i;tasks.add(pool.submit(()->{start.await();String prefix="Concurrent batch "+batch;
     if(policies)f.service.updatePolicies(f.hotel,List.of(policy(f.categoryName,prefix+" A"),policy(f.categoryName,prefix+" B")),f.owner);
     else f.service.updateQuestions(f.hotel,List.of(question(prefix+" A"),question(prefix+" B")),f.owner);return null;
    }));}
    start.countDown();for(var task:tasks)task.get(60,TimeUnit.SECONDS);
    var rows=policies?f.policies():f.questions();assertThat(rows).hasSize(2);
    assertThat(rows.stream().map(text->text.substring(0,text.length()-2)).distinct()).hasSize(1);
   }finally{pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);}
  }
 }
 @Test void failureAfterDeleteAndFirstInsertRollsBackTheEntireReplacement(){
  try(var f=new Fixture()){
   f.service.updateQuestions(f.hotel,List.of(question("Synthetic original")),f.owner);
   assertThatThrownBy(()->f.service.updateQuestions(f.hotel,List.of(question("Synthetic new"),question("Synthetic forced rollback")),f.owner))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   assertThat(f.questions()).containsExactly("Synthetic original");
  }
 }
 @Test void nullReplacementIsInvalidButExplicitEmptyListClears(){
  try(var f=new Fixture()){
   f.service.updateQuestions(f.hotel,List.of(question("Synthetic original")),f.owner);
   assertThatThrownBy(()->f.service.updateQuestions(f.hotel,null,f.owner)).isInstanceOf(IllegalArgumentException.class);
   assertThat(f.questions()).containsExactly("Synthetic original");
   f.service.updateQuestions(f.hotel,List.of(),f.owner);assertThat(f.questions()).isEmpty();
  }
 }
 @Test void embeddedHotelUpdateHonorsContentAndDistinguishesOmittedFromExplicitEmpty(){
  try(var f=new Fixture()){
   var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
   var payload=mapper.convertValue(Map.of("questions",List.of(question("Embedded synthetic FAQ")),"policies",List.of(policy(f.categoryName,"Embedded synthetic policy"))),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class);
   f.service.updateHotel(f.hotel,payload,f.owner);
   assertThat(f.questions()).containsExactly("Embedded synthetic FAQ");assertThat(f.policies()).containsExactly("Embedded synthetic policy");
   f.service.updateHotel(f.hotel,mapper.convertValue(Map.of(),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class),f.owner);
   assertThat(f.questions()).containsExactly("Embedded synthetic FAQ");
   f.service.updateHotel(f.hotel,mapper.convertValue(Map.of("questions",List.of()),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class),f.owner);
   assertThat(f.questions()).isEmpty();assertThat(f.policies()).containsExactly("Embedded synthetic policy");
  }
 }
 @Test void embeddedContentFailureAlsoRollsBackScalarHotelChanges(){
  try(var f=new Fixture()){
   var payload=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(Map.of("name","Synthetic changed name","policies",List.of(policy("Synthetic missing category "+UUID.randomUUID(),"Synthetic policy"))),com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest.class);
   assertThatThrownBy(()->f.service.updateHotel(f.hotel,payload,f.owner)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
   assertThat(f.db.queryForObject("SELECT name FROM hotels WHERE id=?",String.class,f.hotel)).isEqualTo("Synthetic content integrity hotel");
   assertThat(f.policies()).isEmpty();
  }
 }
 @Test void anotherOwnerCannotReplaceOrClearEitherContentCollection(){
  try(var f=new Fixture()){
   f.service.addQuestion(f.hotel,question("Synthetic original"),f.owner);
   f.service.addPolicy(f.hotel,policy(f.categoryName,"Synthetic original policy"),f.owner);
   UUID other=UUID.randomUUID();
   assertThatThrownBy(()->f.service.updateQuestions(f.hotel,List.of(),other)).isInstanceOf(SecurityException.class);
   assertThatThrownBy(()->f.service.clearPolicies(f.hotel,other)).isInstanceOf(SecurityException.class);
   assertThat(f.questions()).containsExactly("Synthetic original");assertThat(f.policies()).containsExactly("Synthetic original policy");
  }
 }
}
