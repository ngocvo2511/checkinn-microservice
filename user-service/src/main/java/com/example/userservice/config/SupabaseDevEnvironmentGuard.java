package com.example.userservice.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Checked after config data and before datasource creation. Values never enter errors. */
public final class SupabaseDevEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
    private static void check(boolean ok) { if (!ok) throw new IllegalStateException("Unsafe or missing supabase-dev configuration; see Batch6B reproduction guide (values redacted)"); }
    private static String required(ConfigurableEnvironment e,String key) {
        String value=e.getProperty(key); check(value!=null && !value.isBlank() && !value.contains("${")); return value;
    }
    public void postProcessEnvironment(ConfigurableEnvironment e,SpringApplication app) {
        Set<String> profiles=new HashSet<>(Arrays.asList(e.getActiveProfiles()));
        if (!profiles.contains("supabase-dev")) { check(!(profiles.contains("local") && profiles.contains("canonical"))); return; }
        try { validate(e,profiles); }
        catch(Exception ex) { throw new IllegalStateException("Unsafe or missing supabase-dev configuration (values redacted)"); }
    }
    private void validate(ConfigurableEnvironment e,Set<String> profiles) {
        check(!profiles.contains("local") && !profiles.contains("canonical"));
        String url=required(e,"USER_DB_URL"),user=required(e,"USER_DB_USERNAME"),pass=required(e,"USER_DB_PASSWORD");
        check(url.equals(required(e,"spring.datasource.url")) && user.equals(required(e,"spring.datasource.username")) && pass.equals(required(e,"spring.datasource.password")));
        check(url.startsWith("jdbc:postgresql://")); URI u=URI.create(url.substring(5));
        check(u.getHost()!=null && u.getUserInfo()==null && u.getFragment()==null && u.getPort()==5432 && "/postgres".equals(u.getPath()));
        check(u.getHost().matches("db\\.[a-z]{20}\\.supabase\\.co|aws-[0-9]+-[a-z0-9-]+\\.pooler\\.supabase\\.com"));
        String project=required(e,"USER_DB_PROJECT_REF"),role=required(e,"USER_DB_RUNTIME_ROLE");
        check(project.matches("[a-z]{20}") && role.matches("[a-z][a-z0-9_]{2,62}"));
        check(!Set.of("postgres","anon","authenticated","service_role").contains(role));
        if (u.getHost().startsWith("db.")) check(u.getHost().equals("db."+project+".supabase.co") && user.equals(role));
        else check(user.equals(role+"."+project));
        Map<String,String> q=new HashMap<>();
        for(String part:Objects.toString(u.getRawQuery(),"").split("&")) {
            String[] kv=part.split("=",2);check(kv.length==2);
            String key=URLDecoder.decode(kv[0],StandardCharsets.UTF_8),value=URLDecoder.decode(kv[1],StandardCharsets.UTF_8);
            check(Set.of("sslmode","sslrootcert","currentSchema","connectTimeout","socketTimeout","ApplicationName").contains(key) && q.put(key,value)==null);
        }
        check("verify-full".equals(q.get("sslmode")) && "public".equals(q.get("currentSchema")) && "10".equals(q.get("connectTimeout")));
        check(q.containsKey("sslrootcert") && !q.get("sslrootcert").isBlank());
        check("validate".equals(required(e,"spring.jpa.hibernate.ddl-auto")) && "never".equals(required(e,"spring.sql.init.mode")));
        check("false".equals(required(e,"spring.flyway.enabled")) && "false".equals(required(e,"spring.flyway.baseline-on-migrate")) && "true".equals(required(e,"spring.flyway.clean-disabled")));
        check("false".equals(required(e,"spring.cloud.config.enabled")) && "false".equals(required(e,"spring.jpa.show-sql")));
        check("SET TIME ZONE 'UTC'".equals(required(e,"spring.datasource.hikari.connection-init-sql")));
        check("public".equals(required(e,"spring.jpa.properties.hibernate.default_schema")) && "UTC".equals(required(e,"spring.jpa.properties.hibernate.jdbc.time_zone")));
        for(String key:List.of("spring.datasource.hikari.jdbc-url","spring.datasource.hikari.username","spring.datasource.hikari.password","spring.datasource.hikari.data-source-class-name","spring.jpa.properties.hibernate.hbm2ddl.auto","spring.jpa.properties.jakarta.persistence.schema-generation.database.action","spring.jpa.properties.javax.persistence.schema-generation.database.action")) check(e.getProperty(key)==null);
        check(!org.springframework.boot.context.properties.bind.Binder.get(e).bind("spring.datasource.hikari.data-source-properties",org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class,String.class)).isBound());
        check("true".equals(required(e,"SUPABASE_DEV_DATABASES_READY")) && "true".equals(required(e,"SUPABASE_DEV_INFRA_ISOLATED")));
        int max=Integer.parseInt(required(e,"USER_DB_POOL_MAX")),budget=Integer.parseInt(required(e,"USER_DB_CONNECTION_BUDGET"));
        check(max>=1 && max<=4 && max+2+3<=budget);
        check(max==Integer.parseInt(required(e,"spring.datasource.hikari.maximum-pool-size")));
        check("1".equals(required(e,"spring.datasource.hikari.minimum-idle")));
        boolean container="true".equals(e.getProperty("SUPABASE_DEV_CONTAINER"));
        check((container ? "rabbitmq" : "localhost").equals(required(e,"spring.rabbitmq.host"))
            && (container ? "redis" : "localhost").equals(required(e,"spring.data.redis.host")));
        check(!"/".equals(required(e,"spring.rabbitmq.virtual-host")));
        int rabbitPort=Integer.parseInt(required(e,"spring.rabbitmq.port")),redisPort=Integer.parseInt(required(e,"spring.data.redis.port"));
        check(container ? rabbitPort==5672 && redisPort==6379 : rabbitPort!=5672 && redisPort!=6379);
        check(Integer.parseInt(required(e,"spring.data.redis.database"))>=0);
    }
}
