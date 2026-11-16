package com.checkinn.db;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostedRunnerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    final String ref="abcdefghijklmnopqrst";
    String url(){return "jdbc:postgresql://db."+ref+".supabase.co:5432/postgres?sslmode=verify-full&sslrootcert=C%3A%2Fca.crt&currentSchema=public&connectTimeout=10";}
    @Test void permitsDirectAndSessionWithExactTlsContract() {
        assertThat(HostedRunner.validateUrl(url(),ref,"direct").getPort()).isEqualTo(5432);
        assertThat(HostedRunner.validateUrl(url().replace("db."+ref+".supabase.co","aws-1-ap-northeast-2.pooler.supabase.com"),ref,"session").getPort()).isEqualTo(5432);
    }
    @Test void postgres17IsAcceptedButUntestedMajorsStayBlocked() {
        for(int major:List.of(14,15,16,17)) HostedRunner.supportedMajor(major);
        for(int major:List.of(13,18,19)) assertThatThrownBy(()->HostedRunner.supportedMajor(major)).hasMessage("PINNED_FLYWAY_VERSION_REVIEW_REQUIRED");
        assertThat(org.flywaydb.core.Flyway.class.getProtectionDomain().getCodeSource().getLocation().toString()).contains("flyway-core-10.22.0.jar");
        assertThat(org.flywaydb.database.postgresql.PostgreSQLDatabase.class.getProtectionDomain().getCodeSource().getLocation().toString()).contains("flyway-database-postgresql-10.22.0.jar");
    }
    @Test void rejectsDangerousConnectionOptionsBeforeConnecting() {
        for(String u:List.of(url().replace("verify-full","require"),url().replace(":5432",":6543"),url().replace(ref,"zyxwvutsrqponmlkjihg"),url().replace("/postgres?","/other?"),url()+"&sslmode=disable",url()+"&sslfactory=anything",url()+"&options=-c%20role%3Dpostgres",url()+"&user=postgres",url()+"&%73slmode=require",url().replace("db."+ref+".supabase.co","127.0.0.1"),url().replace("db.","secret@db."))) {
            assertThatThrownBy(()->HostedRunner.validateUrl(u,ref,"direct")).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->HostedRunner.validateUrl(url(),ref,"transaction")).hasMessage("SESSION_OR_DIRECT_ONLY");
    }
    @Test void environmentAndFiveProjectMappingFailClosedWithoutSecrets() {
        Properties p=new Properties();p.setProperty("environment","local");
        assertThatThrownBy(()->HostedRunner.target(p,"user")).hasMessage("OWNER_ENVIRONMENT");
        p.setProperty("environment","supabase-dev");
        for(String o:HostedRunner.OWNERS){p.setProperty(o+".project",ref);p.setProperty(o+".login",o);}
        assertThatThrownBy(()->HostedRunner.target(p,"user")).hasMessage("DUPLICATE_PROJECT");
        assertThatThrownBy(()->HostedRunner.target(p,"place")).hasMessage("OWNER_ENVIRONMENT");
    }
    @Test void projectByProjectSetupStillRequiresFiveDistinctRefsAndRejectsDuplicateConfiguredLogins() {
        Properties p=new Properties();int i=0;
        for(String owner:HostedRunner.OWNERS)p.setProperty(owner+".project","aaaaaaaaaaaaaaaaaaa"+(char)('a'+i++));
        p.setProperty("user.login","user_migrator");HostedRunner.validateMappings(p);
        p.setProperty("hotel.login","user_migrator");
        assertThatThrownBy(()->HostedRunner.validateMappings(p)).hasMessage("DUPLICATE_LOGIN");
        p.remove("hotel.login");p.remove("hotel.project");
        assertThatThrownBy(()->HostedRunner.validateMappings(p)).hasMessage("MISSING_MANIFEST_FIELD");
    }
    HostedRunner.Target target(){return new HostedRunner.Target("user",ref,"direct",url(),"migrator","migrator",16,"hash","hash",new Properties());}
    Connection identityConnection(String user) throws Exception {
        return identityConnection(user,true);
    }
    Connection identityConnection(String user,boolean backendTls) throws Exception {
        Connection c=mock(Connection.class);
        Object[] values={"postgres",user,"migrator","160004","public",backendTls,0L};
        Statement[] statements=new Statement[values.length];
        for(int i=0;i<values.length;i++) {
            statements[i]=mock(Statement.class);ResultSet r=mock(ResultSet.class);
            when(statements[i].executeQuery(anyString())).thenReturn(r);when(r.next()).thenReturn(true);when(r.getObject(1)).thenReturn(values[i]);
        }
        when(c.createStatement()).thenReturn(statements[0],Arrays.copyOfRange(statements,1,statements.length));return c;
    }
    @Test void observingAnUnencryptedPoolerBackendNeverAuthorizesMigrationOrDirectPlaintext() throws Exception {
        var original=target();
        var session=new HostedRunner.Target(original.owner(),original.project(),"session",original.url(),original.login(),original.sqlUser(),original.major(),original.migrationSha(),original.inventorySha(),original.manifest());
        HostedRunner.identity(identityConnection("migrator",false),session,true);
        assertThatThrownBy(()->HostedRunner.identity(identityConnection("migrator",false),session,false)).hasMessage("SQL_BACKEND_TLS_REQUIRED_FOR_MIGRATION");
        assertThatThrownBy(()->HostedRunner.identity(identityConnection("migrator",false),original,true)).hasMessage("SQL_BACKEND_TLS_REQUIRED_FOR_MIGRATION");
    }
    @Test void eachPhysicalConnectionIsCheckedAndSecondMismatchClosesBeforeFlyway() throws Exception {
        DataSource delegate=mock(DataSource.class);Connection good=identityConnection("migrator"),bad=identityConnection("wrong_owner");
        when(delegate.getConnection()).thenReturn(good,bad);
        var ds=new HostedRunner.GuardedSource(target(),delegate,false,(c,t)->new HostedRunner.ClientTls("TLSv1.3"));
        assertThat(ds.getConnection()).isSameAs(good);
        assertThatThrownBy(ds::getConnection).isInstanceOf(SQLException.class).hasMessage("CONNECTION_GUARD_SQL_ROLE");
        verify(bad).close();verify(good).setReadOnly(true);verify(good).rollback();verify(good,never()).commit();
        assertThatThrownBy(()->ds.getConnection("override","secret")).hasMessage("NO_CREDENTIAL_OVERRIDE");
    }
    @Test void diagnosticCodesNeverForwardDriverMessagesOrSecrets() {
        assertThat(HostedRunner.safeFailureCode(new SQLException("password=synthetic-do-not-log jdbc:postgresql://private","42501"))).isEqualTo("SAFE_STOP_SQLSTATE_42501");
        assertThat(HostedRunner.safeFailureCode(new IllegalStateException("password=synthetic-do-not-log"))).isEqualTo("SAFE_STOP_IllegalStateException");
        assertThat(HostedRunner.safeFailureCode(new SQLException("CONNECTION_GUARD_SQL_TLS"))).isEqualTo("CONNECTION_GUARD_SQL_TLS");
    }
    @Test void unexpectedObjectsAndHistoryStopWithoutDdlOrDml() throws Exception {
        Connection c=mock(Connection.class);Statement s=mock(Statement.class);ResultSet r=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(s);when(s.executeQuery(anyString())).thenReturn(r);when(r.next()).thenReturn(true);when(r.getObject(1)).thenReturn("flyway_schema_history");
        assertThatThrownBy(()->HostedRunner.cleanBusinessSchema(c)).hasMessage("EXISTING_HISTORY_STOP");
        verify(s,never()).execute(anyString());verify(s,never()).executeUpdate(anyString());
        reset(r);when(r.next()).thenReturn(true);when(r.getObject(1)).thenReturn(null,1L);
        assertThatThrownBy(()->HostedRunner.cleanBusinessSchema(c)).hasMessage("UNEXPECTED_PUBLIC_OBJECTS_STOP");
        verify(s,never()).execute(anyString());verify(s,never()).executeUpdate(anyString());
    }
    @Test void approvalBindsExactTargetHashesScopeExpiryAndSecurity() throws Exception {
        var manifest=temp.resolve("synthetic-manifest");var v1=temp.resolve("synthetic-v1");
        java.nio.file.Files.writeString(manifest,"synthetic");java.nio.file.Files.writeString(v1,"synthetic");
        Properties a=new Properties();
        a.setProperty("operation","canonical-v1");a.setProperty("environment","supabase-dev");a.setProperty("service","user");a.setProperty("project",ref);
        a.setProperty("manifestSha256",HostedRunner.sha(manifest));a.setProperty("v1Sha256",HostedRunner.sha(v1));a.setProperty("inventorySha256","hash");
        a.setProperty("approvedBy","synthetic-test-only");a.setProperty("expiresAt",java.time.Instant.now().plusSeconds(300).toString());
        for(String key:List.of("mappingApproved","migrationApproved","securityReviewed","dataApiDisabledOrPublicUnexposed","exclusiveDdlWindow","appsStopped"))a.setProperty(key,"true");
        HostedRunner.validateApproval(target(),manifest,v1,a);
        for(var entry:Map.of("project","wrong","service","hotel","manifestSha256","wrong","v1Sha256","wrong","inventorySha256","wrong","migrationApproved","false","securityReviewed","false","expiresAt","2000-01-01T00:00:00Z").entrySet()) {
            Properties invalid=new Properties();invalid.putAll(a);invalid.setProperty(entry.getKey(),entry.getValue());
            assertThatThrownBy(()->HostedRunner.validateApproval(target(),manifest,v1,invalid)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(()->HostedRunner.validateApproval(target(),manifest,v1,new Properties())).isInstanceOf(IllegalStateException.class);
    }
    @Test void eventReviewRequiresExactHashOwnerProjectV1AndSafeAttributes() throws Exception {
        var events=HostedRunner.JSON.createArrayNode();
        for(var entry:HostedRunner.PLATFORM_EVENTS.entrySet()) {
            String name=entry.getKey();var event=HostedRunner.JSON.createObjectNode();
            event.put("name",name);event.put("functionName",entry.getValue());event.put("triggerOwner","supabase_admin");event.put("functionOwner","supabase_admin");
            event.put("functionSchema","extensions");event.put("language","plpgsql");event.put("securityDefiner",false);event.put("enabled","O");
            event.put("functionIdentityArguments","");event.putNull("extension");event.put("definition","synthetic fixture only");
            event.put("functionAcl","synthetic");event.set("config",HostedRunner.JSON.valueToTree(List.of("search_path=\"\"")));
            event.put("event",Set.of("issue_graphql_placeholder","pgrst_drop_watch").contains(name)?"sql_drop":"ddl_command_end");
            if(name.startsWith("issue_"))event.set("tags",HostedRunner.JSON.valueToTree(List.of(name.equals("issue_graphql_placeholder")?"DROP EXTENSION":"CREATE EXTENSION")));
            else event.putNull("tags");events.add(event);
        }
        var review=HostedRunner.JSON.createObjectNode();review.put("formatVersion",1);review.put("status","STATIC_REVIEWED_NOT_MIGRATION_APPROVAL");
        review.put("owner","user");review.put("project",ref);review.put("serverMajor",17);review.put("v1Sha256","hash");review.set("eventTriggers",events);
        var file=temp.resolve("review.json");HostedRunner.JSON.writeValue(file.toFile(),review);
        Properties p=new Properties();p.setProperty("user.eventTriggerReviewSha256",HostedRunner.sha(file));
        var target=new HostedRunner.Target("user",ref,"direct",url(),"migrator","migrator",17,"hash","hash",p);
        assertThat(HostedRunner.checkedEventReview(target,file).size()).isEqualTo(6);
        p.setProperty("user.eventTriggerReviewSha256","wrong");
        assertThatThrownBy(()->HostedRunner.checkedEventReview(target,file)).hasMessage("EVENT_REVIEW_HASH");
        for(String key:List.of("owner","project","v1Sha256","status")) {
            var invalid=review.deepCopy();invalid.put(key,"wrong");HostedRunner.JSON.writeValue(file.toFile(),invalid);p.setProperty("user.eventTriggerReviewSha256",HostedRunner.sha(file));
            assertThatThrownBy(()->HostedRunner.checkedEventReview(target,file)).isInstanceOf(IllegalStateException.class);
        }
        var invalid=review.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.path("eventTriggers").get(0)).put("securityDefiner",true);
        HostedRunner.JSON.writeValue(file.toFile(),invalid);p.setProperty("user.eventTriggerReviewSha256",HostedRunner.sha(file));
        assertThatThrownBy(()->HostedRunner.checkedEventReview(target,file)).hasMessage("EVENT_REVIEW_ATTRIBUTES");
    }
}
