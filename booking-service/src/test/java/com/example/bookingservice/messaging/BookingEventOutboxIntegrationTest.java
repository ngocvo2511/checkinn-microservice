package com.example.bookingservice.messaging;
import static com.example.bookingservice.contract.TestIds.*;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class BookingEventOutboxIntegrationTest {
    private <T> T proxy(T target,JpaTransactionManager manager) {
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (T)factory.getProxy();
    }
    @Test void sourceAndEventsRollbackTogetherAndConfirmedReplayKeepsIdentity() throws Exception {
        String url=System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_booking");
        var source=new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForMap("SELECT current_database() db,current_user usr"))
                .containsEntry("db","checkinn_booking").containsEntry("usr","checkinn_test");
        var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
        factory.setPackagesToScan("com.example.bookingservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));factory.afterPropertiesSet();
        var emf=factory.getObject();var em=SharedEntityManagerCreator.createSharedEntityManager(emf);
        var manager=new JpaTransactionManager(emf);manager.setDataSource(source);var tx=new TransactionTemplate(manager);
        var outbox=proxy(new BookingEventOutbox(jdbc,new ObjectMapper().findAndRegisterModules()),manager);
        org.springframework.test.util.ReflectionTestUtils.setField(outbox,"exchange","hotel.events");
        var rabbit=mock(RabbitTemplate.class);var delivery=proxy(new BookingEventDelivery(jdbc,rabbit,100,1),manager);
        String id=UUID.randomUUID().toString(), fault="outbox_probe_"+UUID.randomUUID().toString().replace("-","");
        var pool=Executors.newFixedThreadPool(4);
        try {
            assertThat(jdbc.update("INSERT INTO bookings SELECT (jsonb_populate_record(NULL::bookings,to_jsonb(b)||jsonb_build_object('id',?,'status','PENDING','check_in_date','2027-01-10','check_out_date','2027-01-11'))).* FROM bookings b LIMIT 1",id)).isEqualTo(1);
            assertThatThrownBy(()->outbox.enqueue(id(id),"booking.status.changed",Map.of("bookingId",id)))
                    .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
            tx.executeWithoutResult(s->{
                var booking=em.find(Booking.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);booking.setStatus(BookingStatus.CONFIRMED);
                outbox.enqueue(id(id),"booking.status.changed",BookingStatusSnapshots.from(booking));s.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_event_outbox WHERE booking_id=?",Integer.class,id)).isZero();
            jdbc.execute("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic outbox commit fault'; RETURN NEW; END $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON booking_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.booking_id='"+id+"') EXECUTE FUNCTION "+fault+"()");
            assertThatThrownBy(()->tx.executeWithoutResult(s->{var b=em.find(Booking.class,id);b.setStatus(BookingStatus.CONFIRMED);outbox.enqueue(id(id),"booking.status.changed",BookingStatusSnapshots.from(b));})).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_event_outbox WHERE booking_id=?",Integer.class,id)).isZero();
            jdbc.execute("DROP TRIGGER "+fault+" ON booking_event_outbox");
            var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<4;i++) {final int n=i;jobs.add(pool.submit(()->{gate.await();tx.executeWithoutResult(s->{
                var b=em.find(Booking.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                b.setStatus(n%2==0?BookingStatus.CONFIRMED:BookingStatus.CANCELLED);
                outbox.enqueue(id(id),"booking.status.changed",BookingStatusSnapshots.from(b));
            });return null;}));}
            gate.countDown();for(var job:jobs)job.get(30,TimeUnit.SECONDS);
            var events=jdbc.queryForList("SELECT id,event_version,payload::text FROM booking_event_outbox WHERE booking_id=? ORDER BY event_version",id);
            assertThat(events).hasSize(4);
            assertThat(events.stream().map(row->row.get("event_version")).distinct().count()).isEqualTo(4);
            String status=jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id);
            assertThat(new ObjectMapper().readTree((String)events.getLast().get("payload")).get("bookingStatus").asText()).isEqualTo(status);
            UUID eventId=(UUID)events.getFirst().get("id");
            doThrow(new IllegalStateException("synthetic-private-transport-marker")).when(rabbit).send(anyString(),anyString(),any(org.springframework.amqp.core.Message.class),any(CorrelationData.class));
            delivery.deliver(eventId);
            assertThat(jdbc.queryForObject("SELECT state FROM booking_event_outbox WHERE id=?",String.class,eventId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT last_error FROM booking_event_outbox WHERE id=?",String.class,eventId)).isEqualTo("BROKER_UNAVAILABLE");
            var sent=new ArrayList<String>();
            doAnswer(call->{
                var data=call.getArgument(3,CorrelationData.class);sent.add(data.getId());
                data.getFuture().complete(new CorrelationData.Confirm(true,null));return null;
            }).when(rabbit).send(anyString(),anyString(),any(org.springframework.amqp.core.Message.class),any(CorrelationData.class));
            jdbc.update("UPDATE booking_event_outbox SET next_attempt_at=now() WHERE id=?",eventId);
            jdbc.execute("CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON booking_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id='"+eventId+"' AND NEW.state='DELIVERED') EXECUTE FUNCTION "+fault+"()");
            assertThatThrownBy(()->delivery.deliver(eventId)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT state FROM booking_event_outbox WHERE id=?",String.class,eventId)).isEqualTo("PENDING");
            jdbc.execute("DROP TRIGGER "+fault+" ON booking_event_outbox");delivery.deliver(eventId);delivery.deliver(eventId);
            assertThat(sent).containsExactly(eventId.toString(),eventId.toString());
            assertThat(jdbc.queryForObject("SELECT state FROM booking_event_outbox WHERE id=?",String.class,eventId)).isEqualTo("DELIVERED");
            assertThat(jdbc.queryForObject("SELECT payload->>'eventId' FROM booking_event_outbox WHERE id=?",String.class,eventId)).isEqualTo(eventId.toString());
        } finally {
            pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);
            jdbc.execute("DROP TRIGGER IF EXISTS "+fault+" ON booking_event_outbox");jdbc.execute("DROP FUNCTION IF EXISTS "+fault+"()");
            jdbc.update("DELETE FROM booking_event_outbox WHERE booking_id=?",id);
            jdbc.update("DELETE FROM bookings WHERE id=?",id);factory.destroy();
        }
    }
}
