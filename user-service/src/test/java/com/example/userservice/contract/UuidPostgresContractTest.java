package com.example.userservice.contract;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.example.userservice.model.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CHECKINN_UUID_TEST",matches="true")
class UuidPostgresContractTest {

    private final String schema="batch5a_uuid_"+UUID.randomUUID().toString().replace("-", "");
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void isolatedTarget() throws Exception {
        String url=System.getenv("CHECKINN_UUID_USER_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55485/batch5a_uuid_user?currentSchema=batch5a_uuid");
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url.replace("currentSchema=batch5a_uuid","currentSchema="+schema),"batch5a_owner",System.getenv("CHECKINN_UUID_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("batch5a_uuid_user");
        assertThat(java.nio.file.Path.of(jdbc.queryForObject("SHOW data_directory",String.class)).toRealPath())
            .isEqualTo(java.nio.file.Path.of(System.getenv("CHECKINN_UUID_DATA_DIR")).toRealPath());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?",Long.class,schema)).isZero();
        jdbc.execute("CREATE SCHEMA "+schema);
        System.out.println("UUID_TEST_NAMESPACE "+schema);
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.example.userservice.model");
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
    @Test void loyaltyReferencesRoundTripAndCommandKeyKeepsLegacyShape() {
        UUID booking=UUID.fromString("d3616870-e674-310a-b1ee-6a282806ec94"),user=UUID.fromString("11111111-1111-4111-8111-111111111111");
        var command=LoyaltyCommand.builder().commandKey(booking+"|EARN").bookingId(booking).userId(user).commandType("EARN").resultPoints(1L).build();
        var reservation=PointsReservation.builder().bookingId(booking).userId(user).requestedPoints(10L).totalAmount(java.math.BigDecimal.TEN)
            .points(1L).discountAmount(java.math.BigDecimal.ONE).redemptionRate(java.math.BigDecimal.ONE).state(PointsReservationState.HELD)
            .holdExpiresAt(java.time.Instant.now().plusSeconds(600)).nextCheckAt(java.time.Instant.now().plusSeconds(600)).build();
        var entry=PointsTransaction.builder().userId(user).bookingId(booking).transactionType("EARN").points(1L).description("Synthetic").build();
        tx.executeWithoutResult(status->{em.persist(command);em.persist(reservation);em.persist(entry);em.flush();});
        var repositories=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        var reservations=repositories.getRepository(com.example.userservice.repository.PointsReservationRepository.class);
        var points=repositories.getRepository(com.example.userservice.repository.PointsTransactionRepository.class);
        tx.executeWithoutResult(status->{assertThat(reservations.findById(booking).orElseThrow().getBookingId()).isEqualTo(booking);
            assertThat(points.findByBookingId(booking)).hasSize(1);assertThat(points.hasAmbiguousEarningOrUse(user)).isFalse();
            new com.example.userservice.service.LoyaltyCommandLock(jdbc).acquire(booking,user);});
        assertThat(jdbc.queryForObject("SELECT command_key FROM loyalty_commands WHERE booking_id=?",String.class,booking)).isEqualTo("d3616870-e674-310a-b1ee-6a282806ec94|EARN");
        for(String table:java.util.List.of("loyalty_commands","points_reservations","points_transactions"))uuidColumn(table,"booking_id");
    }
}
