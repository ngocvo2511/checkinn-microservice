package com.checkinn.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BATCH6B_LOCAL_CANONICAL_TEST",matches="true")
class LocalManagedEventReviewTest {
    static void sql(Connection c,String text) throws Exception {try(var s=c.createStatement()){s.execute(text);}}
    @Test void reviewedMetadataAllowsCanonicalDdlButChangedBodiesOwnersFlagsOrExtraTriggersStop() throws Exception {
        String base=System.getenv("BATCH6B_LOCAL_TEST_URL");
        assertThat(base).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/batch6b_guard");
        String url=base.replace("/batch6b_guard","/batch6b_events");
        String migrationPassword=UUID.randomUUID().toString();
        Path root=Path.of(System.getenv("BATCH6B_LOCAL_TEST_ROOT"));
        Path v1=root.resolve("user-service/src/main/resources/db/canonical/V1__canonical_schema.sql");
        Path file=Path.of(System.getenv("BATCH6B_LOCAL_PLAN_DIR")).getParent().resolve("synthetic-event-review.json");
        Properties p=new Properties();
        var t=new HostedRunner.Target("user","abcdefghijklmnopqrst","direct","unused","batch6b_guard_admin","batch6b_guard_admin",17,HostedRunner.sha(v1),"unused",p);
        PGSimpleDataSource source=new PGSimpleDataSource();source.setUrl(url);source.setUser("batch6b_guard_admin");source.setPassword(System.getenv("BATCH6B_LOCAL_TEST_PASSWORD"));
        try(Connection c=source.getConnection()) {
            assertThat(HostedRunner.scalar(c,"show cluster_name")).isEqualTo("batch6b_disposable_guards");
            sql(c,"CREATE ROLE supabase_admin NOLOGIN SUPERUSER; CREATE SCHEMA extensions");
            for(var entry:HostedRunner.PLATFORM_EVENTS.entrySet()) {
                String name=entry.getKey(),function=entry.getValue();
                String body=name.startsWith("pgrst_")?"PERFORM pg_notify('checkinn_probe','reload');":"NULL;";
                sql(c,"CREATE FUNCTION extensions."+function+"() RETURNS event_trigger LANGUAGE plpgsql SET search_path TO '' AS $$ BEGIN "+body+" END $$");
                sql(c,"ALTER FUNCTION extensions."+function+"() OWNER TO supabase_admin");
                String event=Set.of("issue_graphql_placeholder","pgrst_drop_watch").contains(name)?"sql_drop":"ddl_command_end";
                String tag=name.startsWith("issue_")?" WHEN TAG IN ('"+(name.equals("issue_graphql_placeholder")?"DROP EXTENSION":"CREATE EXTENSION")+"')":"";
                sql(c,"CREATE EVENT TRIGGER "+name+" ON "+event+tag+" EXECUTE FUNCTION extensions."+function+"(); ALTER EVENT TRIGGER "+name+" OWNER TO supabase_admin");
            }
            assertThatThrownBy(()->HostedRunner.reviewedEventTriggers(c,t)).hasMessage("EVENT_TRIGGERS_REQUIRE_REVIEW");
            var review=HostedRunner.JSON.createObjectNode();review.put("formatVersion",1);review.put("status","STATIC_REVIEWED_NOT_MIGRATION_APPROVAL");
            review.put("owner","user");review.put("project",t.project());review.put("serverMajor",17);review.put("v1Sha256",t.migrationSha());
            review.set("eventTriggers",HostedRunner.eventDescriptors(c));HostedRunner.JSON.writeValue(file.toFile(),review);
            p.setProperty("user.eventTriggerReviewFile",file.toString());p.setProperty("user.eventTriggerReviewSha256",HostedRunner.sha(file));
            HostedRunner.cleanBusinessSchema(c,t);
            for(String change:List.of(
                "CREATE EVENT TRIGGER unexpected ON ddl_command_end EXECUTE FUNCTION extensions.pgrst_ddl_watch()",
                "CREATE OR REPLACE FUNCTION extensions.pgrst_ddl_watch() RETURNS event_trigger LANGUAGE plpgsql SET search_path TO '' AS $$ BEGIN PERFORM pg_notify('checkinn_probe','changed'); END $$",
                "ALTER FUNCTION extensions.pgrst_ddl_watch() OWNER TO batch6b_guard_admin",
                "ALTER EVENT TRIGGER pgrst_ddl_watch DISABLE",
                "ALTER FUNCTION extensions.pgrst_ddl_watch() SET search_path TO public",
                "REVOKE EXECUTE ON FUNCTION extensions.pgrst_ddl_watch() FROM PUBLIC")) {
                c.setAutoCommit(false);
                try {
                    sql(c,change);
                    assertThatThrownBy(()->HostedRunner.reviewedEventTriggers(c,t)).hasMessage("EVENT_TRIGGER_DRIFT_STOP");
                } finally {c.rollback();c.setAutoCommit(true);}
                HostedRunner.reviewedEventTriggers(c,t);
            }
            sql(c,"CREATE ROLE checkinn_events_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT CONNECTION LIMIT 2 PASSWORD '"+migrationPassword+"'");
            sql(c,"GRANT USAGE, CREATE ON SCHEMA public TO checkinn_events_migrator");
        }
        source.setUser("checkinn_events_migrator");source.setPassword(migrationPassword);
        var flyway=HostedRunner.canonicalFlyway(source,v1.getParent());
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(Connection c=source.getConnection()){HostedRunner.reviewedEventTriggers(c,t);}
    }
}
