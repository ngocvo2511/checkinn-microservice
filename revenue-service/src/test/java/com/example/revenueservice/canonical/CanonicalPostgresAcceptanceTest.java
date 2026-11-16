package com.example.revenueservice.canonical;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="BATCH5B_ACCEPTANCE",matches="true")
class CanonicalPostgresAcceptanceTest {

    private final String schema="public";
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void bootstrap() throws Exception {
        String owner="revenue", db=System.getenv("BATCH5B_DATABASE");
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
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name<>'flyway_schema_history'",Long.class)).isEqualTo(5L);
        migrationJdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO "+app);
        migrationJdbc.execute("REVOKE ALL ON flyway_schema_history FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON revenue_cash_transactions FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON revenue_event_receipts FROM "+app);
        migrationJdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+app);
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,app,System.getenv("BATCH5B_APP_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SHOW timezone",String.class)).isEqualTo("UTC");
        assertThatThrownBy(()->jdbc.execute("ALTER TABLE booking_status_records ADD COLUMN forbidden integer")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("DROP TABLE booking_status_records")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE forbidden(id integer)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM flyway_schema_history")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT has_database_privilege(current_user,?,'CONNECT')",Boolean.class,"batch5b_"+(owner.equals("user")?"hotel":"user"))).isFalse();
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.example.revenueservice");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema","public","hibernate.jdbc.time_zone","UTC","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.revenueservice.repository.BookingStatusRecordRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.revenueservice.repository.PaymentRecordRepository.class).count()).isZero();
    }
    @org.junit.jupiter.api.AfterEach void close() { if(factory!=null)factory.destroy(); }
    private void uuidColumn(String table,String column) {
        assertThat(jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?",String.class,schema,table,column)).isEqualTo("uuid");
    }
    @Test void nativeProjectionCashCreditDebitAndMalformedReceiptKeepContracts() {
        var repositories=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        var payments=repositories.getRepository(com.example.revenueservice.repository.PaymentRecordRepository.class);
        var bookings=repositories.getRepository(com.example.revenueservice.repository.BookingStatusRecordRepository.class);
        var cash=new com.example.revenueservice.service.CashTransactionWriter(jdbc);
        var service=new com.example.revenueservice.service.EventIngestionService(payments,bookings,jdbc,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),cash);
        String booking="d3616870-e674-310a-b1ee-6a282806ec94",hotel="22222222-2222-4222-8222-222222222222",customer="11111111-1111-4111-8111-111111111111";
        var from=java.time.LocalDate.of(2027,1,10);var at=java.time.LocalDateTime.of(2027,1,1,12,0);
        var event=new com.example.revenueservice.messaging.event.BookingStatusEvent(booking,hotel,null,from,from.plusDays(1),1,1,"CONFIRMED",at,1L,UUID.randomUUID());
        tx.executeWithoutResult(status->{service.saveBookingStatusEvent(event);service.saveBookingStatusEvent(event);});
        tx.executeWithoutResult(status->assertThat(bookings.findByBookingId(UUID.fromString(booking)).orElseThrow().getBookingStatus()).isEqualTo("CONFIRMED"));
        var malformed=new com.example.revenueservice.messaging.event.BookingStatusEvent("not-a-uuid",hotel,null,null,null,0,0,"PENDING",null,1L,UUID.randomUUID());
        tx.executeWithoutResult(status->service.saveBookingStatusEvent(malformed));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_event_receipts WHERE booking_id='not-a-uuid' AND disposition='REVIEW_REQUIRED'",Long.class)).isEqualTo(1L);
        String payment=UUID.randomUUID().toString();
        for(String state:java.util.List.of("COMPLETED","REFUNDED")) {
            var movement=new com.example.revenueservice.messaging.event.PaymentEvent(booking,customer,hotel,null,null,null,null,null,null,from,from.plusDays(1),1,1,1,java.math.BigDecimal.TEN,state,"VNPAY",at,at,2L,UUID.randomUUID(),payment,"provider:opaque/receipt",java.time.Instant.parse("2027-01-01T05:00:00Z"));
            tx.executeWithoutResult(status->{assertThat(cash.record(movement)).isEqualTo(com.example.revenueservice.service.CashTransactionWriter.Outcome.RECORDED);assertThat(cash.record(movement)).isEqualTo(com.example.revenueservice.service.CashTransactionWriter.Outcome.RECORDED);});
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revenue_cash_transactions",Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForList("SELECT kind FROM revenue_cash_transactions ORDER BY kind",String.class)).containsExactly("CREDIT","DEBIT");
        for(String table:java.util.List.of("payment_records","booking_status_records","revenue_cash_transactions","revenue_cashflow_gaps"))uuidColumn(table,"booking_id");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? AND table_name='revenue_event_receipts' AND column_name='booking_id'",String.class,schema)).isEqualTo("text");
    }
}
