package com.checkinn.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

/** Never accepts a hosted URL. Called only by test-local.py against its new owned cluster. */
@EnabledIfEnvironmentVariable(named="BATCH6B_LOCAL_GUARD_TEST",matches="true")
class LocalCatalogTest {
    @Test void realPostgresCatalogDefaultsAndUnexpectedObjects() throws Exception {
        String url=System.getenv("BATCH6B_LOCAL_TEST_URL");
        assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/batch6b_guard");
        try(Connection c=DriverManager.getConnection(url,"batch6b_guard_admin",System.getenv("BATCH6B_LOCAL_TEST_PASSWORD"))) {
            assertThat(HostedRunner.scalar(c,"show cluster_name")).isEqualTo("batch6b_disposable_guards");
            PathCheck.readonlyInspection(c);
            try(var s=c.createStatement()) {
                s.execute("create role guard_app login");
                s.execute("grant usage on schema public to guard_app");
            }
            Properties p=new Properties();p.setProperty("user.runtimeRole","guard_app");
            var t=new HostedRunner.Target("user","abcdefghijklmnopqrst","direct","unused","batch6b_guard_admin","batch6b_guard_admin",18,"unused","unused",p);
            assertThatThrownBy(()->HostedRunner.clientTls(c,t)).hasMessage("CLIENT_TLS_REQUIRED");
            HostedRunner.cleanBusinessSchema(c);
            String before=HostedRunner.inventory(c);
            assertThatThrownBy(()->HostedRunner.security(c,t)).hasMessage("UNSAFE_CREATOR_DEFAULT_ACL");
            try(var s=c.createStatement()){s.execute("alter default privileges revoke execute on functions from public");}
            HostedRunner.security(c,t);
            assertThat(HostedRunner.inventory(c)).isNotEqualTo(before);
            try(var s=c.createStatement()){s.execute("alter default privileges grant select on tables to anon");}
            assertThatThrownBy(()->HostedRunner.security(c,t)).hasMessage("UNSAFE_CREATOR_DEFAULT_ACL");
            try(var s=c.createStatement()){s.execute("alter default privileges revoke select on tables from anon; grant create on schema public to guard_app");}
            assertThatThrownBy(()->HostedRunner.security(c,t)).hasMessage("RUNTIME_DDL_MEMBERSHIP");
            try(var s=c.createStatement()){s.execute("revoke create on schema public from guard_app; create table unexpected(id integer)");}
            assertThatThrownBy(()->HostedRunner.cleanBusinessSchema(c)).hasMessage("UNEXPECTED_PUBLIC_OBJECTS_STOP");
            // Prove failed preflight did not remove or rewrite the unexpected object.
            assertThat(HostedRunner.scalar(c,"select count(*) from unexpected")).isEqualTo(0L);
            PathCheck.eventInspection(c);
        }
    }

    static class PathCheck {
        static void eventInspection(Connection c) throws Exception {
            // Synthetic fixture only in the new harness-owned DB; retained with the stopped cluster.
            try(var s=c.createStatement()) {
                s.execute("CREATE SCHEMA batch6b_event_probe");
                s.execute("CREATE FUNCTION batch6b_event_probe.reload_probe() RETURNS event_trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$ BEGIN PERFORM pg_notify('checkinn_probe','reload'); END $$");
                s.execute("CREATE EVENT TRIGGER batch6b_event_probe ON ddl_command_end WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION batch6b_event_probe.reload_probe()");
            }
            String sql=java.nio.file.Files.readString(java.nio.file.Path.of("event-trigger-readonly.sql"));
            try(var s=c.createStatement()) {
                boolean result=s.execute(sql);int jsonResults=0;
                while(true) {
                    if(result) try(var rows=s.getResultSet()) {
                        while(rows.next()) {
                            var json=HostedRunner.JSON.readTree(rows.getString(1));
                            assertThat(json.path("identity").path("database").asText()).isEqualTo("batch6b_guard");
                            assertThat(json.path("eventTriggers").size()).isEqualTo(1);
                            var trigger=json.path("eventTriggers").get(0);
                            assertThat(trigger.path("functionSchema").asText()).isEqualTo("batch6b_event_probe");
                            assertThat(trigger.path("functionName").asText()).isEqualTo("reload_probe");
                            assertThat(trigger.path("securityDefiner").asBoolean()).isTrue();
                            assertThat(trigger.path("definition").asText()).contains("pg_notify");
                            assertThat(trigger.path("config").toString()).contains("search_path=pg_catalog");
                            jsonResults++;
                        }
                    }
                    else if(s.getUpdateCount()==-1) break;
                    result=s.getMoreResults();
                }
                assertThat(jsonResults).isEqualTo(1);
            }
        }
        static void readonlyInspection(Connection c) throws Exception {
            var path=java.nio.file.Path.of("admin-preflight-readonly.sql");
            String sql=java.nio.file.Files.readString(path);
            try(var s=c.createStatement()) {
                boolean result=s.execute(sql);int jsonResults=0;
                while(true) {
                    if(result) try(var rows=s.getResultSet()) {
                        while(rows.next()) {
                            var json=HostedRunner.JSON.readTree(rows.getString(1));
                            assertThat(json.path("identity").path("database").asText()).isEqualTo("batch6b_guard");
                            assertThat(json.path("historyExists").asBoolean()).isFalse();
                            assertThat(json.path("dataApiExposure").asText()).contains("NOT VERIFIED");
                            jsonResults++;
                        }
                    }
                    else if(s.getUpdateCount()==-1) break;
                    result=s.getMoreResults();
                }
                assertThat(jsonResults).isEqualTo(1);
            }
        }
    }
}
