package com.example.revenueservice.contract;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CHECKINN_UUID_TEST",matches="true")
class UuidPostgresContractTest {

    private final String schema="batch5a_uuid_"+UUID.randomUUID().toString().replace("-", "");
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void isolatedTarget() throws Exception {
        String url=System.getenv("CHECKINN_UUID_REVENUE_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55485/batch5a_uuid_revenue?currentSchema=batch5a_uuid");
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url.replace("currentSchema=batch5a_uuid","currentSchema="+schema),"batch5a_owner",System.getenv("CHECKINN_UUID_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("batch5a_uuid_revenue");
        assertThat(java.nio.file.Path.of(jdbc.queryForObject("SHOW data_directory",String.class)).toRealPath())
            .isEqualTo(java.nio.file.Path.of(System.getenv("CHECKINN_UUID_DATA_DIR")).toRealPath());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?",Long.class,schema)).isZero();
        jdbc.execute("CREATE SCHEMA "+schema);
        System.out.println("UUID_TEST_NAMESPACE "+schema);
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.example.revenueservice.entity");
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
    @Test void nativeProjectionCashCreditDebitAndMalformedReceiptKeepContracts() {
        jdbc.execute("CREATE TABLE revenue_event_receipts(id uuid PRIMARY KEY,kind varchar(16),booking_id text,event_hash varchar(64),event_at timestamp,disposition varchar(32),reason varchar(64),payload jsonb,received_at timestamptz DEFAULT now(),UNIQUE(kind,event_hash))");
        jdbc.execute("CREATE TABLE revenue_cashflow_gaps(booking_id uuid PRIMARY KEY,hotel_id uuid NOT NULL,source_record_id uuid,reason varchar(64),detected_at timestamptz DEFAULT now(),resolved_at timestamptz,resolved_by uuid,resolution_evidence text)");
        jdbc.execute("CREATE TABLE revenue_cash_transactions(id uuid PRIMARY KEY,source_event_id uuid UNIQUE NOT NULL,source_version bigint,payment_id uuid NOT NULL,kind varchar(16),transaction_id varchar(255),occurred_at timestamptz,booking_id uuid NOT NULL,customer_id uuid NOT NULL,hotel_id uuid NOT NULL,room_type_id uuid,check_in_date date,check_out_date date,nights integer,rooms integer,amount numeric,payment_method varchar(255),recorded_at timestamptz DEFAULT now(),execution_mode varchar(32),UNIQUE(payment_id,kind))");
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
