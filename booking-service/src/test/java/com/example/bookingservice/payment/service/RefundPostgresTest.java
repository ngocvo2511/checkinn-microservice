package com.example.bookingservice.payment.service;
import com.example.bookingservice.booking.entity.*;
import com.example.bookingservice.booking.enums.*;
import com.example.bookingservice.booking.repository.*;
import com.example.bookingservice.payment.entity.*;
import com.example.bookingservice.payment.enums.*;
import com.example.bookingservice.payment.repository.*;
import com.example.bookingservice.payment.refund.*;
import com.example.bookingservice.messaging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.math.BigDecimal;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class RefundPostgresTest {
 static <T>T proxy(T value,JpaTransactionManager tm) {var f=new ProxyFactory(value);f.setProxyTargetClass(true);f.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));return (T)f.getProxy();}
 static void admin(){org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));}
 @Test @EnabledIfEnvironmentVariable(named="CHECKINN_REFUND_TEST",matches="true")
 void actualRefundTransactions() throws Exception {
  String url=System.getenv("CHECKINN_REFUND_BOOKING_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/refund_booking");
  var ds=new DriverManagerDataSource(url,"postgres","");var jdbc=new JdbcTemplate(ds);
  boolean restart="retry".equals(System.getenv("CHECKINN_REFUND_STAGE"));
  var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.example.bookingservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
  factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",restart?"none":"create","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
  var emf=factory.getObject();var em=SharedEntityManagerCreator.createSharedEntityManager(emf);var tm=new JpaTransactionManager(emf);tm.setDataSource(ds);var tx=new TransactionTemplate(tm);
  var repos=new JpaRepositoryFactory(em);var payments=repos.getRepository(PaymentRepository.class);var bookings=repos.getRepository(BookingRepository.class);var requests=repos.getRepository(RefundRequestRepository.class);
  var refunds=proxy(new RefundRequestService(requests,payments,bookings),tm);
  var json=new ObjectMapper().findAndRegisterModules();var outbox=new BookingEventOutbox(jdbc,json);ReflectionTestUtils.setField(outbox,"exchange","hotel.events");
  var service=new PaymentService(payments,bookings,new com.example.bookingservice.payment.vnpay.VnPayProperties(),null,new HotelEventPublisher(proxy(outbox,tm)),null,e->{},refunds);
  ReflectionTestUtils.setField(service,"refundJdbc",jdbc);ReflectionTestUtils.setField(service,"sandboxRefundEnabled",true);var app=proxy(service,tm);
  try {
   if(!restart) for(String sql:List.of("manual-refund-v1-20261112.sql","sandbox-internal-refund-v1-20261112.sql","booking-event-outbox-v1-20261107.sql")) jdbc.execute(Files.readString(Path.of("../docker/sql/"+sql)));
   String id;
   if(restart) id=Files.readString(Path.of("target/refund-payment-id.txt"));
   else {
    var b=Booking.builder().id(UUID.randomUUID().toString()).userId("guest").hotelId("hotel").hotelName("Demo").status(BookingStatus.PENDING_PAYMENT).paidAmount(BigDecimal.ZERO).totalAmount(BigDecimal.TEN).checkInDate(LocalDate.of(2027,1,10)).checkOutDate(LocalDate.of(2027,1,11)).adults(1).children(0).contactName("Demo").contactEmail("demo@example.test").contactPhone("0").build();
    var item=BookingItem.builder().booking(b).roomTypeId("room").roomTypeName("Demo").ratePlanId("rate").checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate()).quantity(1).nights(1).unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN).build();b.setItems(List.of(item));
    var p=Payment.builder().bookingId(b.getId()).amount(BigDecimal.TEN).method(PaymentMethod.VNPAY).status(PaymentStatus.PENDING).vnpayOrderId("demo-order").build();
    tx.executeWithoutResult(s->{em.persist(b);em.persist(p);});id=p.getId();
    app.processVNPayCallback("demo-order","00","123456",Instant.parse("2026-11-12T00:00:00Z"));
    tx.executeWithoutResult(s->em.find(Booking.class,b.getId()).setStatus(BookingStatus.CANCELLED));
    refunds.request(id,"guest","Reviewed cancellation");refunds.approve(id,"admin");
    admin();
    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("owner",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER"))));
    assertThatThrownBy(()->app.refundPayment(p.getId())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    admin();
    // Real deferred commit error after application returns, without production fault injection.
    jdbc.execute("CREATE FUNCTION refund_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test commit fault'; END $$");
    jdbc.execute("CREATE CONSTRAINT TRIGGER refund_fault AFTER INSERT ON booking_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION refund_fault()");
    final String pid=id;
    assertThatThrownBy(()->app.refundPayment(pid)).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,id)).isEqualTo("COMPLETED");
    assertThat(jdbc.queryForObject("SELECT status FROM refund_requests WHERE payment_id=?",String.class,id)).isEqualTo("APPROVED_PENDING_EXECUTION");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_refund_executions",Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT paid_amount FROM bookings WHERE id=(SELECT booking_id FROM payments WHERE vnpay_order_id='demo-order')",BigDecimal.class)).isEqualByComparingTo("10");
    jdbc.execute("DROP TRIGGER refund_fault ON booking_event_outbox");
    var pool=Executors.newFixedThreadPool(6);try {
     var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<?>>();
     for(int i=0;i<6;i++) { final int action=i; jobs.add(pool.submit(()->{gate.await();admin();try{
      if(action==0) return refunds.approve(pid,"admin");
      if(action==1) return refunds.reject(pid,"admin");
      return app.refundPayment(pid);
     }catch(org.springframework.web.server.ResponseStatusException conflict){assertThat(conflict.getStatusCode().value()).isEqualTo(409);return null;}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}})); }
     gate.countDown();for(var job:jobs)job.get(30,TimeUnit.SECONDS);
    }finally{pool.shutdownNow();}
    Files.writeString(Path.of("target/refund-payment-id.txt"),id);
   }
   admin();app.refundPayment(id);app.refundPayment(id);
   assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,id)).isEqualTo("REFUNDED");
   assertThat(jdbc.queryForObject("SELECT vnpay_transaction_no FROM payments WHERE id=?",String.class,id)).isEqualTo("123456");
   assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("10");
   assertThat(jdbc.queryForObject("SELECT status FROM refund_requests WHERE payment_id=?",String.class,id)).isEqualTo("REFUNDED");
   assertThat(jdbc.queryForObject("SELECT paid_amount FROM bookings WHERE id=(SELECT booking_id FROM payments WHERE vnpay_order_id='demo-order')",BigDecimal.class)).isEqualByComparingTo("0");
   assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=(SELECT booking_id FROM payments WHERE vnpay_order_id='demo-order')",String.class)).isEqualTo("CANCELLED");
   assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_refund_audit WHERE action='SANDBOX_INTERNAL_REFUNDED'",Integer.class)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_event_outbox WHERE routing_key='payment.refunded'",Integer.class)).isEqualTo(1);
   if(!restart) {
    jdbc.update("INSERT INTO bookings SELECT (jsonb_populate_record(NULL::bookings,to_jsonb(b)||jsonb_build_object('id','pending-booking','paid_amount',10))).* FROM bookings b LIMIT 1");
    jdbc.update("INSERT INTO payments SELECT (jsonb_populate_record(NULL::payments,to_jsonb(p)||jsonb_build_object('id','pending-payment','booking_id','pending-booking','status','COMPLETED','vnpay_order_id','pending-order','vnpay_transaction_no','789'))).* FROM payments p LIMIT 1");
    refunds.request("pending-payment","guest","Pending recovery fixture");refunds.approve("pending-payment","admin");
   }
   var manual=proxy(new ManualRefundService(refunds,jdbc),tm);
   new ManualRefundRecovery(jdbc,manual).recover();
   assertThat(jdbc.queryForObject("SELECT state FROM manual_refund_executions WHERE payment_id='pending-payment'",String.class)).isEqualTo("PENDING_MANUAL_REFUND");
   assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id='pending-payment'",String.class)).isEqualTo("COMPLETED");
   var rows=jdbc.queryForList("SELECT payload::text FROM booking_event_outbox ORDER BY event_version");
   Files.writeString(Path.of("target/refund-events.json"),json.writeValueAsString(rows.stream().map(r->r.get("payload")).toList()));
   if(restart && System.getenv("CHECKINN_REFUND_RABBIT_PORT")!=null) {
    var connection=new org.springframework.amqp.rabbit.connection.CachingConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("CHECKINN_REFUND_RABBIT_PORT")));
    connection.setUsername("refund_test");connection.setPassword("test-only");connection.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
    try {
     var rabbit=new org.springframework.amqp.rabbit.core.RabbitTemplate(connection);rabbit.setMandatory(true);
     var brokerAdmin=new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
     brokerAdmin.declareExchange(new org.springframework.amqp.core.TopicExchange("hotel.events"));
     brokerAdmin.declareQueue(new org.springframework.amqp.core.Queue("refund.integration",true));
     brokerAdmin.declareBinding(new org.springframework.amqp.core.Binding("refund.integration",org.springframework.amqp.core.Binding.DestinationType.QUEUE,"hotel.events","payment.*",null));
     var delivery=proxy(new BookingEventDelivery(jdbc,rabbit,10000,1),tm);
     for(var eventId:jdbc.queryForList("SELECT id FROM booking_event_outbox ORDER BY event_version",UUID.class)) delivery.deliver(eventId);
     assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_event_outbox WHERE state='DELIVERED'",Integer.class)).isEqualTo(2);
    }finally{connection.destroy();}
   }
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();factory.destroy();}
 }
}
