package com.example.bookingservice.canonical;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.example.bookingservice.booking.entity.*;
import com.example.bookingservice.payment.entity.*;
import com.example.bookingservice.payment.refund.RefundRequest;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="BATCH5B_ACCEPTANCE",matches="true")
class CanonicalPostgresAcceptanceTest {

    private final String schema="public";
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void bootstrap() throws Exception {
        String owner="booking", db=System.getenv("BATCH5B_DATABASE");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        assertThat(db).matches("batch5b_"+owner+"_[0-9]+_[0-9]+");
        String url=System.getenv("BATCH5B_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55486/"+db+"?currentSchema=public");
        String migrator="batch5b_"+owner+"_migrator",app="batch5b_"+owner+"_app";
        var migrationDs=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,migrator,System.getenv("BATCH5B_MIGRATION_PASSWORD"));
        var migrationJdbc=new org.springframework.jdbc.core.JdbcTemplate(migrationDs);
        assertThat(migrationJdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(db);
        assertThat(java.nio.file.Path.of(migrationJdbc.queryForObject("SHOW data_directory",String.class)).toRealPath()).isEqualTo(java.nio.file.Path.of(System.getenv("BATCH5B_DATA_DIR")).toRealPath());
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'",Long.class)).isZero();
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(migrationDs).locations("classpath:db/canonical").defaultSchema("public").schemas("public").baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success AND type='SQL'",Long.class)).isEqualTo(1L);
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name<>'flyway_schema_history'",Long.class)).isEqualTo(7L);
        migrationJdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO "+app);
        migrationJdbc.execute("REVOKE ALL ON flyway_schema_history FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON manual_refund_audit FROM "+app);
        migrationJdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+app);
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,app,System.getenv("BATCH5B_APP_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SHOW timezone",String.class)).isEqualTo("UTC");
        assertThatThrownBy(()->jdbc.execute("ALTER TABLE bookings ADD COLUMN forbidden integer")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("DROP TABLE bookings")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE forbidden(id integer)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM flyway_schema_history")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT has_database_privilege(current_user,?,'CONNECT')",Boolean.class,"batch5b_"+(owner.equals("user")?"hotel":"user"))).isFalse();
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.example.bookingservice");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema","public","hibernate.jdbc.time_zone","UTC","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.bookingservice.payment.refund.RefundRequestRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.bookingservice.payment.repository.PaymentRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.bookingservice.booking.repository.BookingItemRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.bookingservice.booking.repository.BookingRepository.class).count()).isZero();
    }
    @org.junit.jupiter.api.AfterEach void close() { if(factory!=null)factory.destroy(); }
    private void uuidColumn(String table,String column) {
        assertThat(jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?",String.class,schema,table,column)).isEqualTo("uuid");
    }
    @Test void nativeUuidAssociationsGeneratedKeysRepositoryAndBindings() {
        UUID bookingId=UUID.fromString("d3616870-e674-310a-b1ee-6a282806ec94");
        var booking=Booking.builder().id(bookingId).userId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
            .hotelId(UUID.fromString("22222222-2222-4222-8222-222222222222")).hotelName("Native fixture")
            .checkInDate(java.time.LocalDate.of(2027,1,10)).checkOutDate(java.time.LocalDate.of(2027,1,11))
            .adults(2).children(0).status(com.example.bookingservice.booking.enums.BookingStatus.PENDING).totalAmount(java.math.BigDecimal.TEN)
            .paidAmount(java.math.BigDecimal.ZERO).contactName("Fixture").contactEmail("fixture@example.test").contactPhone("123456789").build();
        var item=BookingItem.builder().booking(booking).roomTypeId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
            .roomTypeName("Fixture").ratePlanId("opaque-rate-plan").checkInDate(booking.getCheckInDate()).checkOutDate(booking.getCheckOutDate())
            .quantity(1).nights(1).unitPrice(java.math.BigDecimal.TEN).subtotal(java.math.BigDecimal.TEN).build();
        booking.setItems(java.util.List.of(item));
        var payment=Payment.builder().bookingId(bookingId).amount(java.math.BigDecimal.TEN)
            .method(com.example.bookingservice.payment.enums.PaymentMethod.VNPAY).status(com.example.bookingservice.payment.enums.PaymentStatus.COMPLETED)
            .vnpayOrderId("provider/order-opaque").vnpayTransactionNo("123456789").transactionId("receipt:NOT-UUID").build();
        var refund=new RefundRequest();
        tx.executeWithoutResult(status -> { em.persist(booking);em.persist(payment);em.flush();
            refund.setPaymentId(payment.getId());refund.setBookingId(bookingId);refund.setAmount(java.math.BigDecimal.TEN);refund.setReason("Synthetic");
            refund.setRequestedBy(booking.getUserId());refund.setRequestedAt(java.time.Instant.now());refund.setStatus(RefundRequest.Status.REQUESTED);em.persist(refund);em.flush(); });
        assertThat(item.getId().version()).isEqualTo(4);assertThat(payment.getId().version()).isEqualTo(4);assertThat(refund.getId().version()).isEqualTo(4);
        var repositories=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        var bookings=repositories.getRepository(com.example.bookingservice.booking.repository.BookingRepository.class);
        var payments=repositories.getRepository(com.example.bookingservice.payment.repository.PaymentRepository.class);
        tx.executeWithoutResult(status -> {
            assertThat(bookings.findById(bookingId).orElseThrow().getId()).isEqualTo(bookingId);
            assertThat(payments.findById(payment.getId()).orElseThrow().getVnpayOrderId()).isEqualTo("provider/order-opaque");
            bookings.lockCreationCommand("golden-contract-lock");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE id=?",Long.class,bookingId)).isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT count(*) FROM bookings WHERE id=?",Long.class,bookingId.toString()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT approved_by IS NULL FROM refund_requests WHERE id=?",Boolean.class,refund.getId())).isTrue();
        for(String table:java.util.List.of("bookings","booking_items","payments","refund_requests"))uuidColumn(table,"id");
        uuidColumn("payments","booking_id");uuidColumn("refund_requests","payment_id");uuidColumn("bookings","user_id");
        assertThat(repositories.getRepository(com.example.bookingservice.booking.repository.BookingItemRepository.class).findByRoomTypeId(item.getRoomTypeId())).hasSize(1);
        var requests=repositories.getRepository(com.example.bookingservice.payment.refund.RefundRequestRepository.class);
        var refundService=new com.example.bookingservice.payment.refund.RefundRequestService(requests,payments,bookings);
        var outbox=new com.example.bookingservice.messaging.BookingEventOutbox(jdbc,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        org.springframework.test.util.ReflectionTestUtils.setField(outbox,"exchange","batch5b.isolated");
        var app=new com.example.bookingservice.payment.service.PaymentService(payments,bookings,new com.example.bookingservice.payment.vnpay.VnPayProperties(),null,new com.example.bookingservice.messaging.HotelEventPublisher(outbox),null,event->{},refundService);
        org.springframework.test.util.ReflectionTestUtils.setField(app,"refundJdbc",jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(app,"sandboxRefundEnabled",true);
        tx.executeWithoutResult(status->{var b=em.find(Booking.class,bookingId);b.setStatus(com.example.bookingservice.booking.enums.BookingStatus.CANCELLED);b.setPaidAmount(java.math.BigDecimal.TEN);});
        tx.executeWithoutResult(status->refundService.approve(payment.getId(),booking.getUserId()));
        tx.executeWithoutResult(status->refundService.approve(payment.getId(),booking.getUserId()));
        var manual=new com.example.bookingservice.payment.refund.ManualRefundService(refundService,jdbc);
        tx.executeWithoutResult(status->manual.start(payment.getId(),"SYSTEM_RECOVERY"));
        tx.executeWithoutResult(status->manual.start(payment.getId(),"SYSTEM_RECOVERY"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(booking.getUserId().toString(),null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            tx.executeWithoutResult(status->app.refundPayment(payment.getId()));
            tx.executeWithoutResult(status->app.refundPayment(payment.getId()));
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,payment.getId())).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments WHERE id=?",String.class,payment.getId())).isEqualTo("receipt:NOT-UUID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_refund_executions WHERE payment_id=?",Long.class,payment.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM manual_refund_audit WHERE action='SANDBOX_INTERNAL_REFUNDED'",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_event_outbox WHERE routing_key='payment.refunded'",Long.class)).isEqualTo(1L);
        assertThatThrownBy(()->jdbc.update("DELETE FROM manual_refund_audit")).isInstanceOf(org.springframework.dao.DataAccessException.class);

    }
}
