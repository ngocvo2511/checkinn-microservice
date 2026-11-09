package com.example.regulationsservice.service;

import com.example.regulationsservice.messaging.*;
import com.example.regulationsservice.model.Regulation;
import com.example.regulationsservice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class RegulationTransactionIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaRepositories(basePackageClasses=RegulationRepository.class)
    static class Config {
        @Bean DriverManagerDataSource dataSource() {
            String url=System.getenv("CHECKINN_RANGE_DB_URL");
            assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_regulations");
            return new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
            factory.setPackagesToScan("com.example.regulationsservice.model");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false",
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory,DriverManagerDataSource source) {
            var manager=new JpaTransactionManager(factory);manager.setDataSource(source);return manager;
        }
        @Bean JdbcTemplate jdbc(DriverManagerDataSource source) { return new JdbcTemplate(source); }
        @Bean RegulationEventPublisher publisher(JdbcTemplate jdbc) {
            return new RegulationEventPublisher(jdbc,new ObjectMapper().findAndRegisterModules(),"hotel.events");
        }
        @Bean RegulationCacheProvider provider(RegulationRepository rows,RegulationSnapshotRepository snapshots,RegulationEventPublisher events) {
            return new RegulationCacheProvider(rows,snapshots,events);
        }
        @Bean RabbitTemplate rabbit() { return mock(RabbitTemplate.class); }
        @Bean RegulationEventDelivery delivery(JdbcTemplate jdbc,RabbitTemplate rabbit) { return new RegulationEventDelivery(jdbc,rabbit,100,1); }
    }
    @Test void sourceAuditAndOutboxAreAtomicUnderConcurrencyRollbackAndDeliveryReplay() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc=context.getBean(JdbcTemplate.class);var provider=context.getBean(RegulationCacheProvider.class);
            var publisher=context.getBean(RegulationEventPublisher.class);var delivery=context.getBean(RegulationEventDelivery.class);
            var rabbit=context.getBean(RabbitTemplate.class);var tx=new TransactionTemplate(context.getBean(JpaTransactionManager.class));
            assertThat(jdbc.queryForMap("SELECT current_database() db,current_user usr")).containsEntry("db","checkinn_regulations").containsEntry("usr","checkinn_test");
            String key="TEST_"+UUID.randomUUID().toString().replace("-",""),fault="reg_probe_"+UUID.randomUUID().toString().replace("-","");
            var pool=Executors.newFixedThreadPool(8);
            try {
                assertThatThrownBy(()->publisher.publish(new RegulationEvent())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
                var first=provider.saveRegulation(new Regulation(key,"Unicode \"name\"\n","2","Path\\line\n",true,0),"synthetic-admin");
                assertThat(first.getVersion()).isEqualTo(1);
                var audit=jdbc.queryForObject("SELECT snapshot_data FROM regulation_snapshots WHERE regulation_key=?",String.class,key);
                assertThat(new ObjectMapper().readTree(audit).get("name").asText()).isEqualTo(first.getName());
                provider.saveRegulation(new Regulation(key,"Updated","3",null,true,1),"synthetic-admin");
                assertThat(provider.getRegulation(key).orElseThrow().getVersion()).isEqualTo(2);
                var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();
                for(int i=0;i<8;i++)tasks.add(pool.submit(()->{
                    gate.await();try {provider.saveRegulation(new Regulation(key,"Concurrent","4",null,true,2),"synthetic-admin");return true;}
                    catch(org.springframework.dao.OptimisticLockingFailureException conflict){return false;}
                    catch(org.springframework.web.server.ResponseStatusException conflict){assertThat(conflict.getStatusCode().value()).isEqualTo(409);return false;}
                }));
                gate.countDown();int wins=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))wins++;
                assertThat(wins).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT version FROM regulations WHERE regulation_key=?",Integer.class,key)).isEqualTo(3);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM regulation_snapshots WHERE regulation_key=?",Integer.class,key)).isEqualTo(3);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key=?",Integer.class,key)).isEqualTo(3);
                tx.executeWithoutResult(s->{provider.saveRegulation(new Regulation(key,"Rolled back","9",null,true,3),"synthetic-admin");s.setRollbackOnly();});
                assertThat(provider.getRegulation(key).orElseThrow().getValue()).isEqualTo("4");
                jdbc.execute("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic regulation commit fault'; RETURN NEW; END $$");
                jdbc.execute("CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON regulation_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.regulation_key='"+key+"') EXECUTE FUNCTION "+fault+"()");
                assertThatThrownBy(()->provider.saveRegulation(new Regulation(key,"Commit fault","8",null,true,3),"synthetic-admin")).isInstanceOf(RuntimeException.class);
                jdbc.execute("DROP TRIGGER "+fault+" ON regulation_event_outbox");
                assertThat(provider.getRegulation(key).orElseThrow().getValue()).isEqualTo("4");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM regulation_snapshots WHERE regulation_key=?",Integer.class,key)).isEqualTo(3);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key=?",Integer.class,key)).isEqualTo(3);
                UUID event=jdbc.queryForObject("SELECT id FROM regulation_event_outbox WHERE regulation_key=? AND source_version=1",UUID.class,key);
                doThrow(new IllegalStateException("synthetic-private-transport")).when(rabbit).send(anyString(),anyString(),any(org.springframework.amqp.core.Message.class),any(CorrelationData.class));
                delivery.deliver(event);
                assertThat(jdbc.queryForObject("SELECT last_error FROM regulation_event_outbox WHERE id=?",String.class,event)).isEqualTo("BROKER_UNAVAILABLE");
                var sent=new ArrayList<String>();
                doAnswer(i->{var data=i.getArgument(3,CorrelationData.class);sent.add(data.getId());data.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
                        .when(rabbit).send(anyString(),anyString(),any(org.springframework.amqp.core.Message.class),any(CorrelationData.class));
                jdbc.update("UPDATE regulation_event_outbox SET next_attempt_at=now() WHERE id=?",event);
                jdbc.execute("CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON regulation_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id='"+event+"' AND NEW.state='DELIVERED') EXECUTE FUNCTION "+fault+"()");
                assertThatThrownBy(()->delivery.deliver(event)).isInstanceOf(RuntimeException.class);
                assertThat(jdbc.queryForObject("SELECT state FROM regulation_event_outbox WHERE id=?",String.class,event)).isEqualTo("PENDING");
                jdbc.execute("DROP TRIGGER "+fault+" ON regulation_event_outbox");delivery.deliver(event);delivery.deliver(event);
                assertThat(sent).containsExactly(event.toString(),event.toString());
                assertThat(jdbc.queryForObject("SELECT state FROM regulation_event_outbox WHERE id=?",String.class,event)).isEqualTo("DELIVERED");
            }finally {
                pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);
                jdbc.execute("DROP TRIGGER IF EXISTS "+fault+" ON regulation_event_outbox");jdbc.execute("DROP FUNCTION IF EXISTS "+fault+"()");
                jdbc.update("DELETE FROM regulation_event_outbox WHERE regulation_key=?",key);
                jdbc.update("DELETE FROM regulation_snapshots WHERE regulation_key=?",key);
                jdbc.update("DELETE FROM regulations WHERE regulation_key=?",key);
            }
        }
    }
}
