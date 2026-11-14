package com.example.regulationsservice.canonical;
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
        String owner="regulations", db=System.getenv("BATCH5B_DATABASE");
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
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name<>'flyway_schema_history'",Long.class)).isEqualTo(3L);
        migrationJdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO "+app);
        migrationJdbc.execute("REVOKE ALL ON flyway_schema_history FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON regulation_snapshots FROM "+app);
        migrationJdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+app);
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,app,System.getenv("BATCH5B_APP_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SHOW timezone",String.class)).isEqualTo("UTC");
        assertThatThrownBy(()->jdbc.execute("ALTER TABLE regulations ADD COLUMN forbidden integer")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("DROP TABLE regulations")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE forbidden(id integer)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM flyway_schema_history")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT has_database_privilege(current_user,?,'CONNECT')",Boolean.class,"batch5b_"+(owner.equals("user")?"hotel":"user"))).isFalse();
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.example.regulationsservice");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema","public","hibernate.jdbc.time_zone","UTC","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.regulationsservice.repository.RegulationRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.regulationsservice.repository.RegulationSnapshotRepository.class).count()).isZero();
    }
    @org.junit.jupiter.api.AfterEach void close() { if(factory!=null)factory.destroy(); }
    private void uuidColumn(String table,String column) {
        assertThat(jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?",String.class,schema,table,column)).isEqualTo("uuid");
    }
    @org.junit.jupiter.api.Test void javaBootstrapSnapshotsAndOutboxAreIdempotent() {
        var repos=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        var regulations=repos.getRepository(com.example.regulationsservice.repository.RegulationRepository.class);
        var snapshots=repos.getRepository(com.example.regulationsservice.repository.RegulationSnapshotRepository.class);
        var publisher=new com.example.regulationsservice.messaging.RegulationEventPublisher(jdbc,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),"batch5b.isolated");
        var provider=new com.example.regulationsservice.service.RegulationCacheProvider(regulations,snapshots,publisher);
        tx.executeWithoutResult(s->provider.initializeCache());
        tx.executeWithoutResult(s->provider.initializeCache());
        assertThat(regulations.count()).isEqualTo(4);
        assertThat(snapshots.count()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM regulation_event_outbox",Long.class)).isEqualTo(4L);
        assertThat(regulations.findByRegulationKey("COMMISSION_RATE").orElseThrow().getValue()).isEqualTo("0.10");
    }

}
