package com.example.revenueservice.service;

import com.example.revenueservice.entity.*;
import com.example.revenueservice.repository.*;
import com.example.revenueservice.messaging.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class RevenueEventOrderingIntegrationTest {
    @Test void actualTransactionsSerializeFirstInsertAndKeepReceiptAtomic() throws Exception {
        String url=System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_revenue");
        var source=new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForMap("SELECT current_database() db,current_user usr"))
                .containsEntry("db","checkinn_revenue").containsEntry("usr","checkinn_test");
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
        factory.setPackagesToScan("com.example.revenueservice.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
        var emf=factory.getObject();var em=SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repositories=new JpaRepositoryFactory(em);
        var service=new EventIngestionService(repositories.getRepository(PaymentRecordRepository.class),
                repositories.getRepository(BookingStatusRecordRepository.class),jdbc,new ObjectMapper().findAndRegisterModules(),new CashTransactionWriter(jdbc));
        var manager=new JpaTransactionManager(emf);manager.setDataSource(source);
        var tx=new TransactionTemplate(manager);
        var ids=java.util.stream.IntStream.range(0,6).mapToObj(i->UUID.randomUUID().toString()).toList();
        String hotel=UUID.randomUUID().toString(), fault="revenue_probe_"+UUID.randomUUID().toString().replace("-","");
        var time=LocalDateTime.of(2027,1,1,12,0);var from=LocalDate.of(2027,1,10);var to=from.plusDays(1);
        java.util.function.BiFunction<String,Integer,BookingStatusEvent> event=(id,n)->new BookingStatusEvent(id,hotel,null,from,to,1,1,n==7?"CANCELLED":"CONFIRMED",time.plusSeconds(n),null,null);
        var pool=Executors.newFixedThreadPool(8);
        try {
            var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<8;i++) { final int n=i;jobs.add(pool.submit(()->{gate.await();tx.executeWithoutResult(s->service.saveBookingStatusEvent(event.apply(ids.get(0),n)));return null;})); }
            gate.countDown();for(var job:jobs)job.get(30,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_status_records WHERE booking_id=?",Integer.class,ids.getFirst())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT booking_status FROM booking_status_records WHERE booking_id=?",String.class,ids.getFirst())).isEqualTo("CANCELLED");
            tx.executeWithoutResult(s->service.saveBookingStatusEvent(event.apply(ids.getFirst(),7)));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_event_receipts WHERE booking_id=?",Integer.class,ids.getFirst())).isEqualTo(8);
            var conflicting=new BookingStatusEvent(ids.getFirst(),hotel,null,from,to,1,1,"CONFIRMED",time.plusSeconds(7),null,null);
            tx.executeWithoutResult(s->service.saveBookingStatusEvent(conflicting));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_event_receipts WHERE booking_id=? AND reason='CONFLICTING_SOURCE_TIME'",Integer.class,ids.getFirst())).isEqualTo(1);
            tx.executeWithoutResult(s->service.saveBookingStatusEvent(new BookingStatusEvent(ids.get(1),hotel,null,from,to,1,1,"CONFIRMED",null,null,null)));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_status_records WHERE booking_id=?",Integer.class,ids.get(1))).isZero();
            assertThat(jdbc.queryForObject("SELECT disposition FROM revenue_event_receipts WHERE booking_id=?",String.class,ids.get(1))).isEqualTo("REVIEW_REQUIRED");
            jdbc.execute("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic receipt commit fault'; RETURN NEW; END $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON revenue_event_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.booking_id='"+ids.get(2)+"') EXECUTE FUNCTION "+fault+"()");
            assertThatThrownBy(()->tx.executeWithoutResult(s->service.saveBookingStatusEvent(event.apply(ids.get(2),0)))).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_status_records WHERE booking_id=?",Integer.class,ids.get(2))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_event_receipts WHERE booking_id=?",Integer.class,ids.get(2))).isZero();
            jdbc.execute("DROP TRIGGER "+fault+" ON revenue_event_receipts");jdbc.execute("DROP FUNCTION "+fault+"()");
            tx.executeWithoutResult(s->service.saveBookingStatusEvent(event.apply(ids.get(2),0)));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_status_records WHERE booking_id=?",Integer.class,ids.get(2))).isEqualTo(1);
            String customer=UUID.randomUUID().toString(),payment=UUID.randomUUID().toString();
            var creditAt=Instant.parse("2027-01-31T23:30:00.123456789Z");
            var debitAt=Instant.parse("2027-02-28T17:30:00.123456789Z");
            var credit=new PaymentEvent(ids.get(3),customer,hotel,null,null,null,null,null,null,from,to,1,1,1,
                    new java.math.BigDecimal("100"),"COMPLETED","VNPAY",time,time,1L,UUID.randomUUID(),payment,"synthetic-credit",creditAt);
            var debit=new PaymentEvent(ids.get(3),customer,hotel,null,null,null,null,null,null,from,to,1,1,1,
                    new java.math.BigDecimal("100"),"REFUNDED","VNPAY",time,time.plusDays(1),2L,UUID.randomUUID(),payment,"synthetic-refund",debitAt);
            // Refund first: later arrival of an older CREDIT still appends the missing movement.
            tx.executeWithoutResult(s->service.savePaymentEvent(debit));
            tx.executeWithoutResult(s->service.savePaymentEvent(credit));
            tx.executeWithoutResult(s->service.savePaymentEvent(credit));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions WHERE booking_id=?",Integer.class,ids.get(3))).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT payment_status FROM payment_records WHERE booking_id=?",String.class,ids.get(3))).isEqualTo("REFUNDED");
            var flows=new RevenueCashFlows(jdbc,org.mockito.Mockito.mock(com.example.revenueservice.client.HotelGrpcClient.class),"Asia/Ho_Chi_Minh");
            var reports=new RevenueReportService(flows,repositories.getRepository(BookingStatusRecordRepository.class),
                    org.mockito.Mockito.mock(com.example.revenueservice.client.HotelCapacityClient.class),
                    org.mockito.Mockito.mock(com.example.revenueservice.client.HotelGrpcClient.class),
                    org.mockito.Mockito.mock(com.example.revenueservice.regulations.RegulationClient.class));
            var feb=reports.revenue(hotel,LocalDate.of(2027,2,1),LocalDate.of(2027,2,28),com.example.revenueservice.dto.GroupBy.MONTH);
            assertThat(feb.data()).hasSize(1);assertThat(feb.data().getFirst().amount()).isEqualByComparingTo("100");
            var march=reports.revenue(hotel,LocalDate.of(2027,3,1),LocalDate.of(2027,3,1),com.example.revenueservice.dto.GroupBy.DAY);
            assertThat(march.data()).hasSize(1);assertThat(march.data().getFirst().amount()).isEqualByComparingTo("-100");
            assertThat(reports.revenue(hotel,LocalDate.of(2027,2,28),LocalDate.of(2027,2,28),com.example.revenueservice.dto.GroupBy.DAY).data()).isEmpty();
            var conflict=new PaymentEvent(ids.get(3),customer,hotel,null,null,null,null,null,null,from,to,1,1,1,
                    new java.math.BigDecimal("101"),"COMPLETED","VNPAY",time,time.plusDays(2),3L,UUID.randomUUID(),payment,"synthetic-credit",creditAt);
            tx.executeWithoutResult(s->service.savePaymentEvent(conflict));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions WHERE booking_id=?",Integer.class,ids.get(3))).isEqualTo(2);
            assertThatThrownBy(()->flows.read(hotel,LocalDate.of(2027,2,1),LocalDate.of(2027,3,1)))
                    .isInstanceOf(com.example.revenueservice.exception.ReportReconciliationRequiredException.class);
            // Equal amounts cannot join different bookings into one payment, even
            // when the opposite cash directions arrive concurrently on different locks.
            String sharedPayment=UUID.randomUUID().toString();
            var contenders=List.of(
                    new PaymentEvent(ids.get(4),customer,hotel,null,null,null,null,null,null,from,to,1,1,1,
                            java.math.BigDecimal.TEN,"COMPLETED","VNPAY",time,time,1L,UUID.randomUUID(),sharedPayment,"credit",creditAt),
                    new PaymentEvent(ids.get(5),customer,hotel,null,null,null,null,null,null,from,to,1,1,1,
                            java.math.BigDecimal.TEN,"REFUNDED","VNPAY",time,time,2L,UUID.randomUUID(),sharedPayment,"refund",debitAt));
            var cashGate=new CountDownLatch(1);var cashJobs=new ArrayList<Future<?>>();
            for(var contender:contenders)cashJobs.add(pool.submit(()->{
                cashGate.await();tx.executeWithoutResult(s->service.savePaymentEvent(contender));return null;
            }));
            cashGate.countDown();for(var job:cashJobs)job.get(30,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions WHERE payment_id=?",Integer.class,sharedPayment)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_event_receipts WHERE booking_id IN (?,?) AND reason='TRANSACTION_CONFLICT'",Integer.class,ids.get(4),ids.get(5))).isEqualTo(1);
        } finally {
            pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);
            jdbc.execute("DROP TRIGGER IF EXISTS "+fault+" ON revenue_event_receipts");jdbc.execute("DROP FUNCTION IF EXISTS "+fault+"()");
            for(String id:ids) {
                jdbc.update("DELETE FROM revenue_cashflow_gaps WHERE booking_id=?",id);
                jdbc.update("DELETE FROM revenue_cash_transactions WHERE booking_id=?",id);
                jdbc.update("DELETE FROM payment_records WHERE booking_id=?",id);
                jdbc.update("DELETE FROM revenue_event_receipts WHERE booking_id=?",id);
                jdbc.update("DELETE FROM booking_status_records WHERE booking_id=?",id);
            }
            factory.destroy();
        }
    }
}
