package com.example.hotelservice.Review.service;

import com.example.hotelservice.Review.repository.*;
import com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest;
import com.example.hotelservice.Review.mapper.ReviewMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Hotel.service.UserGrpcClient;
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
class ReviewResponseIntegrationTest {
 @org.springframework.boot.test.context.TestConfiguration
 @EnableTransactionManagement(proxyTargetClass=true)
 @EnableJpaRepositories(basePackageClasses={ReviewResponseRepository.class,HotelRepository.class})
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
  @Bean ReviewService service(HotelReviewRepository reviews,ReviewResponseRepository replies,ReviewFeedbackRepository feedback,HotelRepository hotels){
   // Widen the existing read-before-insert window; SQL/repositories/transactions remain real.
   var slow=mock(ReviewResponseRepository.class,call->{
    if(Set.of("save","saveAndFlush").contains(call.getMethod().getName())) {
     var entity=call.getArgument(0,com.example.hotelservice.Review.entity.ReviewResponse.class);
     if("Concurrent synthetic response".equals(entity.getContent()))Thread.sleep(500);
    }
    try{return call.getMethod().invoke(replies,call.getArguments());}
    catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   return new ReviewService(reviews,slow,feedback,new ReviewMapper(),mock(UserGrpcClient.class),hotels,mock(ReviewEligibilityClient.class));
  }
 }
 static class Fixture implements AutoCloseable {
  final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Config.class);
  final JdbcTemplate db=context.getBean(JdbcTemplate.class);final ReviewService service=context.getBean(ReviewService.class);
  final UUID hotel=UUID.randomUUID(),review=UUID.randomUUID(),owner=UUID.randomUUID(),guest=UUID.randomUUID(),orphan=UUID.randomUUID();
  final List<UUID> extraReviews=new ArrayList<>();
  UUID extra(UUID author,String status){
   UUID id=UUID.randomUUID();extraReviews.add(id);
   db.update("INSERT INTO hotel_reviews(id,hotel_id,guest_id,booking_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES (?,?,?,?,8,'Synthetic review','Synthetic review content',0,0,?,now(),now())",id,hotel,author,UUID.randomUUID(),status);return id;
  }
  Fixture(){
   assertThat(db.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");
   assertThat(db.update("INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id',?,'owner_id',?,'name','Synthetic review integrity hotel','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1",hotel.toString(),owner.toString())).isEqualTo(1);
   db.update("INSERT INTO hotel_reviews(id,hotel_id,guest_id,booking_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES (?,?,?,?,8,'Synthetic review','Synthetic review content',0,0,'PUBLISHED',now(),now())",review,hotel,guest,UUID.randomUUID());
  }
  void reply(UUID parent){db.update("INSERT INTO review_responses(id,review_id,owner_id,content,created_at,updated_at) VALUES (?,?,?,'Synthetic reply',now(),now())",UUID.randomUUID(),parent,owner);}
  long replies(){return db.queryForObject("SELECT count(*) FROM review_responses WHERE review_id=?",Long.class,review);}
  @Override public void close(){
   try{db.update("DELETE FROM review_responses WHERE review_id IN (?,?)",review,orphan);db.update("DELETE FROM review_feedbacks WHERE review_id=?",review);
    for(UUID id:extraReviews)db.update("DELETE FROM hotel_reviews WHERE id=?",id);
    db.update("DELETE FROM hotel_reviews WHERE id=?",review);db.update("DELETE FROM hotels WHERE id=? AND owner_id=?",hotel,owner);
   }finally{context.close();}
  }
 }
 @Test void competingOwnerRepliesProduceOneCommittedResponseAndSevenConflicts() throws Exception {
  try(var f=new Fixture()) {
   var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();
   try {
    for(int i=0;i<8;i++)tasks.add(pool.submit(()->{start.await();
     try{f.service.addReviewResponse(f.review,CreateReviewResponseRequest.builder().content("Concurrent synthetic response").build(),f.owner);return true;}
     catch(IllegalStateException duplicate){return false;}
    }));
    start.countDown();int wins=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))wins++;
    assertThat(wins).isEqualTo(1);assertThat(f.replies()).isEqualTo(1);
   }finally{pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);}
  }
 }
 @Test void authorizedReviewDeletionDoesNotLeaveReplyOrFeedbackOrphans(){
  try(var f=new Fixture()) {
   f.reply(f.review);
   f.db.update("INSERT INTO review_feedbacks(id,review_id,user_id,feedback_type,created_at,updated_at) VALUES (?,?,?,'HELPFUL',now(),now())",UUID.randomUUID(),f.review,UUID.randomUUID());
   f.service.deleteReview(f.review,f.guest,false);
   assertThat(f.replies()).isZero();
   assertThat(f.db.queryForObject("SELECT count(*) FROM review_feedbacks WHERE review_id=?",Long.class,f.review)).isZero();
  }
 }
 @Test void privateHistoryPagesOnlyOwnReviewsIncludingModeratedRowsAndOwnerResponse(){
  try(var f=new Fixture()) {
   f.extra(f.guest,"REJECTED");f.extra(f.guest,"PENDING");UUID foreign=f.extra(UUID.randomUUID(),"PUBLISHED");f.reply(f.review);
   var first=f.service.getMyReviews(f.guest,org.springframework.data.domain.PageRequest.of(0,2));
   var last=f.service.getMyReviews(f.guest,org.springframework.data.domain.PageRequest.of(1,2));
   assertThat(first.getTotalElements()).isEqualTo(3);assertThat(first.getTotalPages()).isEqualTo(2);
   var all=new ArrayList<>(first.getContent());all.addAll(last.getContent());
   assertThat(all).hasSize(3).allSatisfy(row->{assertThat(row.getGuestId()).isEqualTo(f.guest);assertThat(row.getId()).isNotEqualTo(foreign);assertThat(row.getHotelName()).isEqualTo("Synthetic review integrity hotel");});
   assertThat(all.stream().filter(row->row.getId().equals(f.review)).findFirst().orElseThrow().getOwnerResponse()).isNotNull();
   assertThat(f.service.getMyReviews(UUID.randomUUID(),org.springframework.data.domain.PageRequest.of(0,2))).isEmpty();
  }
 }
 @Test void databaseGuardsRejectNewOrphansAndDuplicateReplies(){
  try(var f=new Fixture()) {
   assertThatThrownBy(()->f.reply(f.orphan)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   f.reply(f.review);
   assertThatThrownBy(()->f.reply(f.review)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   assertThat(f.replies()).isEqualTo(1);
  }
 }
}
