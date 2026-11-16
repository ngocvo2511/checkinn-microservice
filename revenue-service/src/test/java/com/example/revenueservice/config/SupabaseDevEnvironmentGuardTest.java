package com.example.revenueservice.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class SupabaseDevEnvironmentGuardTest {
    Map<String,Object> contract() {
        Map<String,Object> m=new HashMap<>();
        m.put("REVENUE_DB_URL","jdbc:postgresql://db.abcdefghijklmnopqrst.supabase.co:5432/postgres?sslmode=verify-full&sslrootcert=C%3A%2Fca.crt&currentSchema=public&connectTimeout=10");
        m.put("REVENUE_DB_PROJECT_REF","abcdefghijklmnopqrst");m.put("REVENUE_DB_RUNTIME_ROLE","checkinn_revenue_app");
        m.put("REVENUE_DB_USERNAME","checkinn_revenue_app");m.put("REVENUE_DB_PASSWORD","synthetic-sentinel-secret");
        m.put("REVENUE_DB_POOL_MAX","4");m.put("REVENUE_DB_CONNECTION_BUDGET","9");
        m.put("SUPABASE_DEV_DATABASES_READY","true");m.put("SUPABASE_DEV_INFRA_ISOLATED","true");
        m.put("SUPABASE_DEV_RABBIT_PORT","5678");m.put("SUPABASE_DEV_RABBIT_USERNAME","synthetic");m.put("SUPABASE_DEV_RABBIT_PASSWORD","synthetic");m.put("SUPABASE_DEV_RABBIT_VHOST","batch6b");
        m.put("SUPABASE_DEV_REDIS_PORT","6388");m.put("SUPABASE_DEV_REDIS_DATABASE","0");return m;
    }
    StandardEnvironment env(Map<String,Object> m,String... profiles) throws Exception {
        StandardEnvironment e=new StandardEnvironment();e.getPropertySources().remove("systemEnvironment");e.getPropertySources().remove("systemProperties");e.setActiveProfiles(profiles);
        for(var source:new YamlPropertySourceLoader().load("base",new ClassPathResource("application.yml"))) e.getPropertySources().addLast(source);
        for(var source:new YamlPropertySourceLoader().load("hosted",new ClassPathResource("application-supabase-dev.yml"))) e.getPropertySources().addFirst(source);
        e.getPropertySources().addFirst(new MapPropertySource("test",m));return e;
    }
    void verify(Map<String,Object> m) throws Exception {new SupabaseDevEnvironmentGuard().postProcessEnvironment(env(m,"supabase-dev"),null);}
    @Test void containerRequiresExactOwnedServiceDnsAndInternalPorts() throws Exception {
        var m=contract();m.put("SUPABASE_DEV_CONTAINER","true");
        m.put("spring.rabbitmq.host","rabbitmq");m.put("spring.data.redis.host","redis");
        m.put("spring.rabbitmq.port","5672");m.put("spring.data.redis.port","6379");verify(m);
        for(var entry:Map.of("spring.rabbitmq.host","shared-broker","spring.data.redis.host","localhost","spring.rabbitmq.port","5678","spring.data.redis.port","6388").entrySet()) {
            var bad=new HashMap<String,Object>(m);bad.put(entry.getKey(),entry.getValue());
            assertThatThrownBy(()->verify(bad)).isInstanceOf(IllegalStateException.class);
        }
        m.remove("SUPABASE_DEV_CONTAINER");assertThatThrownBy(()->verify(m)).isInstanceOf(IllegalStateException.class);
    }
    @Test void actualProfileResolvesToSafeContract() throws Exception {verify(contract());}
    @Test void missingSecretsNeverFallBackOrLeak() throws Exception {
        var m=contract();m.remove("REVENUE_DB_PASSWORD");
        assertThatThrownBy(()->verify(m)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("synthetic-sentinel-secret");
    }
    @Test void unsafeExternalOverridesAreRejected() {
        for(var entry:Map.of("spring.jpa.hibernate.ddl-auto","update","spring.flyway.enabled","true","spring.sql.init.mode","always","spring.datasource.username","postgres","spring.datasource.url","jdbc:postgresql://localhost:5432/demo","spring.cloud.config.enabled","true","spring.jpa.show-sql","true","spring.jpa.properties.hibernate.hbm2ddl.auto","create","spring.datasource.hikari.jdbc-url","jdbc:postgresql://localhost:5432/demo","spring.datasource.hikari.connection-init-sql","CREATE TABLE unsafe(id int)").entrySet()) {
            var m=contract();m.put(entry.getKey(),entry.getValue());assertThatThrownBy(()->verify(m)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void tlsAndSharedInfrastructureFailClosed() {
        for(var entry:Map.of("REVENUE_DB_URL",contract().get("REVENUE_DB_URL").toString().replace("verify-full","require"),"SUPABASE_DEV_INFRA_ISOLATED","false","SUPABASE_DEV_DATABASES_READY","false","SUPABASE_DEV_RABBIT_PORT","5672","SUPABASE_DEV_REDIS_PORT","6379","SUPABASE_DEV_RABBIT_VHOST","/","REVENUE_DB_CONNECTION_BUDGET","8").entrySet()) {
            var m=contract();m.put(entry.getKey(),entry.getValue());assertThatThrownBy(()->verify(m)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void conflictingProfilesStopButLegacyProfileNeedsNoHostedCredentials() throws Exception {
        var guard=new SupabaseDevEnvironmentGuard();
        assertThatThrownBy(()->guard.postProcessEnvironment(env(contract(),"local","supabase-dev"),null)).isInstanceOf(IllegalStateException.class);
        guard.postProcessEnvironment(env(Map.of(),"local"),null);
        guard.postProcessEnvironment(env(Map.of(),"canonical"),null);
    }
    @Test void actualSpringBootstrapInvokesGuardBeforeContextCreation() {
        var app=new org.springframework.boot.SpringApplication(Empty.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("spring.profiles.active","supabase-dev","logging.level.root","OFF","spring.main.banner-mode","off"));
        assertThatThrownBy(()->app.run()).hasStackTraceContaining("values redacted");
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false) static class Empty {}
    @Test void bootstrapRegistrationExists() throws Exception {
        assertThat(new ClassPathResource("META-INF/spring.factories").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains(SupabaseDevEnvironmentGuard.class.getName());
    }
}
