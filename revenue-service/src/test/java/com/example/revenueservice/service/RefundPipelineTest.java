package com.example.revenueservice.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.revenueservice.repository.*;
import com.example.revenueservice.messaging.event.PaymentEvent;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class RefundPipelineTest {
 @Test @EnabledIfEnvironmentVariable(named="CHECKINN_REFUND_TEST",matches="true")
 void persistedBookingOutboxThroughRealIngestion() throws Exception {
  var url=System.getenv("CHECKINN_REFUND_REVENUE_URL");assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/refund_revenue");
  var ds=new DriverManagerDataSource(url,"postgres","");var jdbc=new JdbcTemplate(ds);
  var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.example.revenueservice.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
  try {
   for(String script:List.of("revenue-event-receipts-v1-20261107.sql","revenue-source-versions-v1-20261107.sql","revenue-cash-transactions-v1-20261107.sql","revenue-history-gaps-v1-20261107.sql"))jdbc.execute(Files.readString(Path.of("../docker/sql/"+script)));
   jdbc.update("INSERT INTO revenue_cash_transactions(id,source_event_id,source_version,payment_id,kind,transaction_id,occurred_at,booking_id,customer_id,hotel_id,check_in_date,check_out_date,nights,rooms,amount,payment_method) VALUES (?,?,1,'historical','CREDIT','historical',now(),'historical','guest','hotel','2027-01-10','2027-01-11',1,1,5,'VNPAY')",UUID.randomUUID(),UUID.randomUUID());
   var migration=Files.readString(Path.of("../docker/sql/revenue-sandbox-refund-v1-20261112.sql"));jdbc.execute(migration);jdbc.execute(migration);
   assertThat(jdbc.queryForObject("SELECT amount FROM revenue_cash_transactions WHERE payment_id='historical'",BigDecimal.class)).isEqualByComparingTo("5");
   var em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());var repos=new JpaRepositoryFactory(em);var tm=new JpaTransactionManager(factory.getObject());tm.setDataSource(ds);
   var json=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
   var cash=new CashTransactionWriter(jdbc);ReflectionTestUtils.setField(cash,"sandboxEnabled",true);
   var target=new EventIngestionService(repos.getRepository(PaymentRecordRepository.class),repos.getRepository(BookingStatusRecordRepository.class),jdbc,json,cash);
   var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));
   var listener=new com.example.revenueservice.messaging.EventListener((EventIngestionService)proxy.getProxy());
   var raw=json.readTree(Files.readString(Path.of("../booking-service/target/refund-events.json")));
   var events=new ArrayList<PaymentEvent>();for(var node:raw)events.add(json.readValue(node.asText(),PaymentEvent.class));
   var debit=events.stream().filter(e->"REFUNDED".equals(e.paymentStatus())).findFirst().orElseThrow();var credit=events.stream().filter(e->"COMPLETED".equals(e.paymentStatus())).findFirst().orElseThrow();
   assertThat(debit.executionMode()).isEqualTo("SANDBOX_INTERNAL");assertThat(debit.currency()).isEqualTo("VND");assertThat(debit.transactionId()).isNotEqualTo(credit.transactionId());
   jdbc.execute("CREATE FUNCTION consumer_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test consumer commit fault'; END $$");
   jdbc.execute("CREATE CONSTRAINT TRIGGER consumer_fault AFTER INSERT ON revenue_event_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION consumer_fault()");
   assertThatThrownBy(()->listener.handlePaymentEvent(debit)).isInstanceOf(RuntimeException.class);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions WHERE payment_id=?",Integer.class,debit.paymentId())).isZero();
   jdbc.execute("DROP TRIGGER consumer_fault ON revenue_event_receipts");
   listener.handlePaymentEvent(debit); // refund arrives before original credit
   var pool=Executors.newFixedThreadPool(4);try{var jobs=new ArrayList<Future<?>>();for(int i=0;i<4;i++)jobs.add(pool.submit(()->{listener.handlePaymentEvent(credit);listener.handlePaymentEvent(debit);}));for(var job:jobs)job.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
   if(System.getenv("CHECKINN_REFUND_RABBIT_PORT")!=null) {
    var cf=new com.rabbitmq.client.ConnectionFactory();cf.setHost("127.0.0.1");cf.setPort(Integer.parseInt(System.getenv("CHECKINN_REFUND_RABBIT_PORT")));cf.setUsername("refund_test");cf.setPassword("test-only");
    // Close a real channel after SQL commit but before ACK; broker redelivers.
    try(var connection=cf.newConnection()) {
     var channel=connection.createChannel();var message=channel.basicGet("refund.integration",false);assertThat(message).isNotNull();
     listener.handlePaymentEvent(json.readValue(message.getBody(),PaymentEvent.class));channel.close();
     var next=connection.createChannel();var redelivery=next.basicGet("refund.integration",false);assertThat(redelivery).isNotNull();assertThat(redelivery.getEnvelope().isRedeliver()).isTrue();
     listener.handlePaymentEvent(json.readValue(redelivery.getBody(),PaymentEvent.class));next.basicAck(redelivery.getEnvelope().getDeliveryTag(),false);
     var remaining=next.basicGet("refund.integration",false);assertThat(remaining).isNotNull();listener.handlePaymentEvent(json.readValue(remaining.getBody(),PaymentEvent.class));next.basicAck(remaining.getEnvelope().getDeliveryTag(),false);next.close();
    }
   }
   assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions WHERE payment_id=?",Integer.class,debit.paymentId())).isEqualTo(2);
   assertThat(jdbc.queryForObject("SELECT sum(CASE WHEN kind='CREDIT' THEN amount ELSE -amount END) FROM revenue_cash_transactions WHERE payment_id=?",BigDecimal.class,debit.paymentId())).isEqualByComparingTo("0");
   assertThat(jdbc.queryForObject("SELECT execution_mode FROM revenue_cash_transactions WHERE payment_id=? AND kind='DEBIT'",String.class,debit.paymentId())).isEqualTo("SANDBOX_INTERNAL");
   var report=new RevenueCashFlows(jdbc,null,"Asia/Ho_Chi_Minh").read("hotel",java.time.LocalDate.of(2026,1,1),java.time.LocalDate.of(2027,12,31));
   assertThat(report.stream().map(r->"REFUNDED".equals(r.getPaymentStatus())?r.getAmount().negate():r.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("5");
   assertThat(jdbc.queryForObject("SELECT payment_status FROM payment_records WHERE booking_id=?",String.class,debit.bookingId())).isEqualTo("REFUNDED");
  }finally{factory.destroy();}
 }
}
