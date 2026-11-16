package com.example.userservice.canonical;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.example.userservice.model.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="BATCH5B_ACCEPTANCE",matches="true")
class CanonicalPostgresAcceptanceTest {

    private final String schema="public";
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void bootstrap() throws Exception {
        String owner="user", db=System.getenv("BATCH5B_DATABASE");
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
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON loyalty_commands FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON points_transactions FROM "+app);
        migrationJdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+app);
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,app,System.getenv("BATCH5B_APP_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SHOW timezone",String.class)).isEqualTo("UTC");
        assertThatThrownBy(()->jdbc.execute("ALTER TABLE loyalty_commands ADD COLUMN forbidden integer")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("DROP TABLE loyalty_commands")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE forbidden(id integer)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM flyway_schema_history")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT has_database_privilege(current_user,?,'CONNECT')",Boolean.class,"batch5b_"+(owner.equals("user")?"hotel":"user"))).isFalse();
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.example.userservice");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema","public","hibernate.jdbc.time_zone","UTC","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.LoyaltyCommandRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.LoyaltyPointsRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.OtpRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.PointsReservationRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.PointsTransactionRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.UserProfileRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.userservice.repository.UserRepository.class).count()).isZero();
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
        jdbc.update("UPDATE points_reservations SET redemption_rate=?,discount_amount=? WHERE booking_id=?",new java.math.BigDecimal("0.1234567890123456789"),new java.math.BigDecimal("1.001"),booking);
        assertThat(jdbc.queryForObject("SELECT redemption_rate FROM points_reservations WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("0.1234567890123456789");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM points_reservations WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("1.001");

        var accounts=repositories.getRepository(com.example.userservice.repository.LoyaltyPointsRepository.class);
        var commands=repositories.getRepository(com.example.userservice.repository.LoyaltyCommandRepository.class);
        var rate=org.mockito.Mockito.mock(com.example.userservice.regulations.RegulationClient.class);
        org.mockito.Mockito.when(rate.getEarnConversionRate()).thenReturn(java.math.BigDecimal.TEN);
        org.mockito.Mockito.when(rate.getRedemptionConversionRate()).thenReturn(java.math.BigDecimal.ONE);
        var service=new com.example.userservice.service.LoyaltyPointsService(accounts,points,rate,commands,new com.example.userservice.service.LoyaltyCommandLock(jdbc),reservations);
        UUID loyaltyUser=UUID.randomUUID(),earnedBooking=UUID.randomUUID(),usedBooking=UUID.randomUUID();
        tx.executeWithoutResult(status->service.earnPoints(loyaltyUser,earnedBooking,new java.math.BigDecimal("1000")));
        tx.executeWithoutResult(status->service.earnPoints(loyaltyUser,earnedBooking,new java.math.BigDecimal("1000")));
        var reserve=new com.example.userservice.dto.ReservePointsRequest(loyaltyUser,usedBooking,10L,new java.math.BigDecimal("100"),java.time.Instant.now().plusSeconds(600));
        tx.executeWithoutResult(status->service.reserve(reserve));
        tx.executeWithoutResult(status->service.reserve(reserve));
        var use=new com.example.userservice.dto.ApplyPointsRequest(loyaltyUser,10L,usedBooking,"fixture",java.math.BigDecimal.TEN);
        tx.executeWithoutResult(status->assertThat(service.usePoints(use)).isEqualByComparingTo("10"));
        tx.executeWithoutResult(status->assertThat(service.usePoints(use)).isEqualByComparingTo("10"));
        tx.executeWithoutResult(status->service.refundPoints(loyaltyUser,usedBooking));
        tx.executeWithoutResult(status->service.refundPoints(loyaltyUser,usedBooking));
        assertThat(jdbc.queryForObject("SELECT used_points FROM loyalty_points WHERE user_id=?",Long.class,loyaltyUser)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_points FROM loyalty_points WHERE user_id=?",Long.class,loyaltyUser)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM points_transactions WHERE booking_id=? AND transaction_type='REVERSE_USE'",Long.class,usedBooking)).isEqualTo(1L);

    }
}
