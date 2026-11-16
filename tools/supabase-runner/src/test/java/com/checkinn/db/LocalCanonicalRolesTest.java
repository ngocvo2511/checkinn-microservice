package com.checkinn.db;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Five new harness-owned local databases only; never repoints HostedRunner's target guard. */
@EnabledIfEnvironmentVariable(named="BATCH6B_LOCAL_CANONICAL_TEST",matches="true")
class LocalCanonicalRolesTest {
    static void execute(Connection c,String sql) throws Exception {try(var s=c.createStatement()){s.execute(sql);}}
    static void denied(Connection c,String sql) throws Exception {
        c.setAutoCommit(false);
        try {assertThatThrownBy(()->execute(c,sql)).isInstanceOf(SQLException.class).satisfies(ex->assertThat(((SQLException)ex).getSQLState()).isEqualTo("42501"));}
        finally {c.rollback();c.setAutoCommit(true);}
    }
    static void attest(Connection c,String owner) throws Exception {
        execute(c,"SET checkinn.approved_project_ref='abcdefghijklmnopqrst'; SET checkinn.approved_service='"+owner+"'; SET checkinn.security_reviewed='true'");
    }
    @ParameterizedTest @ValueSource(strings={"user","hotel","booking","revenue","regulations"})
    void freshV1RerunAndExactRuntimeGrants(String owner) throws Exception {
        String base=System.getenv("BATCH6B_LOCAL_TEST_URL");
        assertThat(base).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/batch6b_guard");
        String url=base.replace("/batch6b_guard","/batch6b_roles_"+owner);
        Path plans=Path.of(System.getenv("BATCH6B_LOCAL_PLAN_DIR")).resolve(owner);
        Path root=Path.of(System.getenv("BATCH6B_LOCAL_TEST_ROOT"));
        JsonNode info=HostedRunner.JSON.readTree(Files.readString(plans.resolve("inventory.json")));
        String migrator=info.path("migrator").asText(),app=info.path("runtimeRole").asText();
        String migratorPassword=UUID.randomUUID().toString(),appPassword=UUID.randomUUID().toString();
        Properties p=new Properties();p.setProperty(owner+".runtimeRole",app);
        var target=new HostedRunner.Target(owner,"abcdefghijklmnopqrst","direct","unused",migrator,migrator,17,"unused","unused",p);
        try(Connection admin=DriverManager.getConnection(url,"batch6b_guard_admin",System.getenv("BATCH6B_LOCAL_TEST_PASSWORD"))) {
            assertThat(HostedRunner.scalar(admin,"show cluster_name")).isEqualTo("batch6b_disposable_guards");
            assertThat(HostedRunner.scalar(admin,"show server_version").toString()).startsWith("17.");
            String provision=Files.readString(plans.resolve("01-provision-roles.sql"));
            // Unapproved review scripts must refuse before any CREATE ROLE.
            assertThatThrownBy(()->execute(admin,provision)).hasMessageContaining("approval settings required");
            execute(admin,"ROLLBACK");
            assertThat(HostedRunner.scalar(admin,"select count(*) from pg_roles where rolname='"+migrator+"'")).isEqualTo(0L);
            attest(admin,owner);execute(admin,provision);
            assertThat(HostedRunner.scalar(admin,"select rolcanlogin from pg_roles where rolname='"+migrator+"'")).isEqualTo(false);
            // Roles cannot silently be reused or have their settings overwritten on a rerun.
            assertThatThrownBy(()->execute(admin,provision)).hasMessageContaining("already exists");
            execute(admin,"ROLLBACK");
            execute(admin,"ALTER ROLE "+migrator+" LOGIN PASSWORD '"+migratorPassword+"'; ALTER ROLE "+app+" LOGIN PASSWORD '"+appPassword+"'");
        }
        PGSimpleDataSource source=new PGSimpleDataSource();source.setUrl(url);source.setUser(migrator);source.setPassword(migratorPassword);
        try(Connection c=source.getConnection()) {HostedRunner.cleanBusinessSchema(c);HostedRunner.security(c,target);}
        var flyway=HostedRunner.canonicalFlyway(source,root.resolve(owner+"-service/src/main/resources/db/canonical"));
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(Connection c=source.getConnection()) {
            assertThat(HostedRunner.scalar(c,"select count(*) from public.flyway_schema_history")).isEqualTo(1L);
            assertThat(HostedRunner.scalar(c,"select count(*) from information_schema.tables where table_schema='public' and table_name<>'flyway_schema_history'"))
                .isEqualTo((long)info.path("tables").size());
            // Creator defaults deny API access immediately after V1, BEFORE post-migration revokes.
            for(JsonNode table:info.path("tables")) {
                for(String api:List.of("anon","authenticated","service_role")) {
                    assertThat(HostedRunner.scalar(c,"select has_table_privilege('"+api+"','public."+table.asText()+"','SELECT,INSERT,UPDATE,DELETE')")).isEqualTo(false);
                }
            }
            attest(c,owner);execute(c,Files.readString(plans.resolve("03-postmigration-grants.sql")));
            HostedRunner.security(c,target);
        }
        try(Connection c=DriverManager.getConnection(url,app,appPassword)) {
            assertThat(HostedRunner.scalar(c,"select has_table_privilege(current_user,'public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')")).isEqualTo(false);
            assertThat(HostedRunner.scalar(c,"select pg_has_role(current_user,'"+migrator+"','MEMBER')")).isEqualTo(false);
            denied(c,"CREATE TABLE public.forbidden(id integer)");
            denied(c,"SELECT * FROM public.flyway_schema_history");
            for(JsonNode table:info.path("tables")) {
                String name=table.asText();assertThat(name).matches("[a-z_][a-z0-9_]*");
                boolean readOnly=name.equals("checkinn_feature_migrations");
                boolean appendOnly=Set.of("loyalty_commands","points_transactions","manual_refund_audit","revenue_cash_transactions","revenue_event_receipts","regulation_snapshots").contains(name);
                String column=HostedRunner.scalar(c,"select column_name from information_schema.columns where table_schema='public' and table_name='"+name+"' order by ordinal_position limit 1").toString();
                assertThat(column).matches("[a-z_][a-z0-9_]*");
                // Prove actual SQL permissions with zero-row commands, preserving synthetic fixtures.
                HostedRunner.scalar(c,"select count(*) from public."+name);
                boolean canInsert=(Boolean)HostedRunner.scalar(c,"select has_table_privilege(current_user,'public."+name+"','INSERT')");
                assertThat(canInsert).isEqualTo(!readOnly);
                if(canInsert) execute(c,"INSERT INTO public."+name+" SELECT * FROM public."+name+" WHERE false");
                else denied(c,"INSERT INTO public."+name+" SELECT * FROM public."+name+" WHERE false");
                boolean canUpdate=(Boolean)HostedRunner.scalar(c,"select has_table_privilege(current_user,'public."+name+"','UPDATE')");
                assertThat(canUpdate).isEqualTo(!readOnly && !appendOnly);
                if(canUpdate) execute(c,"UPDATE public."+name+" SET "+column+"="+column+" WHERE false");
                else denied(c,"UPDATE public."+name+" SET "+column+"="+column+" WHERE false");
                boolean canDelete=(Boolean)HostedRunner.scalar(c,"select has_table_privilege(current_user,'public."+name+"','DELETE')");
                assertThat(canDelete).isEqualTo(!readOnly && !appendOnly);
                if(canDelete) execute(c,"DELETE FROM public."+name+" WHERE false");else denied(c,"DELETE FROM public."+name+" WHERE false");
                denied(c,"ALTER TABLE public."+name+" ADD COLUMN forbidden integer");
                for(String api:List.of("anon","authenticated","service_role")) {
                    assertThat(HostedRunner.scalar(c,"select has_table_privilege('"+api+"','public."+name+"','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')")).isEqualTo(false);
                    assertThat(HostedRunner.scalar(c,"select has_any_column_privilege('"+api+"','public."+name+"','SELECT,INSERT,UPDATE,REFERENCES')")).isEqualTo(false);
                }
            }
            for(JsonNode seq:info.path("sequences")) {
                String name=seq.asText();
                assertThat(HostedRunner.scalar(c,"select has_sequence_privilege(current_user,'public."+name+"','USAGE')")).isEqualTo(true);
                assertThat(HostedRunner.scalar(c,"select has_sequence_privilege(current_user,'public."+name+"','UPDATE')")).isEqualTo(false);
            }
            execute(c,Files.readString(plans.resolve("04-readonly-verification.sql")));
        }
    }
}
