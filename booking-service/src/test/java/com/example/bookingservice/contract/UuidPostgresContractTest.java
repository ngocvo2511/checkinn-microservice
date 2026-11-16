package com.example.bookingservice.contract;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.example.bookingservice.booking.entity.*;
import com.example.bookingservice.payment.entity.*;
import com.example.bookingservice.payment.refund.RefundRequest;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CHECKINN_UUID_TEST",matches="true")
class UuidPostgresContractTest {

    private final String schema="batch5a_uuid_"+UUID.randomUUID().toString().replace("-", "");
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void isolatedTarget() throws Exception {
        String url=System.getenv("CHECKINN_UUID_BOOKING_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55485/batch5a_uuid_booking?currentSchema=batch5a_uuid");
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url.replace("currentSchema=batch5a_uuid","currentSchema="+schema),"batch5a_owner",System.getenv("CHECKINN_UUID_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("batch5a_uuid_booking");
        assertThat(java.nio.file.Path.of(jdbc.queryForObject("SHOW data_directory",String.class)).toRealPath())
            .isEqualTo(java.nio.file.Path.of(System.getenv("CHECKINN_UUID_DATA_DIR")).toRealPath());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?",Long.class,schema)).isZero();
        jdbc.execute("CREATE SCHEMA "+schema);
        System.out.println("UUID_TEST_NAMESPACE "+schema);
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.example.bookingservice.booking.entity","com.example.bookingservice.payment.entity","com.example.bookingservice.payment.refund");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        // Fresh disposable metadata only. No update/drop, no Flyway/migration acceptance claim.
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","create-only","hibernate.default_schema",schema,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
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
    }
}
