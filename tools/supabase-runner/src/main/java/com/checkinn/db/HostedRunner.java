package com.checkinn.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

/** Separate operator tool. Never starts an application, grants privileges or loads .env. */
public final class HostedRunner {
    static final List<String> OWNERS = List.of("user", "hotel", "booking", "revenue", "regulations");
    static final ObjectMapper JSON = new ObjectMapper();
    static void require(boolean value, String code) { if (!value) throw new IllegalStateException(code); }
    static String env(String key) { String v = System.getenv(key); require(v != null && !v.isBlank(), "MISSING_" + key); return v; }
    static String field(Properties p, String key) { String v=p.getProperty(key); require(v!=null && !v.isBlank(), "MISSING_MANIFEST_FIELD"); return v; }
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String sha(Path path) throws Exception { return sha(Files.readAllBytes(path)); }

    // Strict URL option allowlist prevents options, SSL factories, credentials, multi-host failover
    // and arbitrary init SQL from escaping the reviewed connection tuple.
    static URI validateUrl(String url, String project, String mode) {
        require(url.startsWith("jdbc:postgresql://"), "JDBC_FORMAT");
        URI u=URI.create(url.substring(5));
        require(u.getUserInfo()==null && u.getFragment()==null && u.getHost()!=null, "JDBC_TUPLE");
        require(u.getPort()==5432 && u.getPath().equals("/postgres"), "JDBC_DATABASE_PORT");
        require(project.matches("[a-z]{20}"), "PROJECT_REFERENCE");
        if (mode.equals("direct")) require(u.getHost().equals("db."+project+".supabase.co"), "DIRECT_HOST");
        else { require(mode.equals("session"), "SESSION_OR_DIRECT_ONLY"); require(u.getHost().matches("aws-[0-9]+-[a-z0-9-]+\\.pooler\\.supabase\\.com"), "SESSION_HOST"); }
        Map<String,String> q=new HashMap<>();
        for(String part:Objects.toString(u.getRawQuery(), "").split("&")) {
            String[] kv=part.split("=",2); require(kv.length==2, "JDBC_OPTION");
            String key=URLDecoder.decode(kv[0],java.nio.charset.StandardCharsets.UTF_8);
            String val=URLDecoder.decode(kv[1],java.nio.charset.StandardCharsets.UTF_8);
            require(Set.of("sslmode","sslrootcert","currentSchema","connectTimeout","socketTimeout","ApplicationName").contains(key) && q.put(key,val)==null, "JDBC_OPTION");
        }
        require("verify-full".equals(q.get("sslmode")) && "public".equals(q.get("currentSchema")), "TLS_SCHEMA");
        require(q.containsKey("sslrootcert") && !q.get("sslrootcert").isBlank(), "CA_REQUIRED");
        require("10".equals(q.get("connectTimeout")), "CONNECT_TIMEOUT");
        return u;
    }

    record Target(String owner, String project, String mode, String url, String login,
                  String sqlUser, int major, String migrationSha, String inventorySha, Properties manifest) {}

    static Target target(Properties p, String owner) throws Exception {
        require(OWNERS.contains(owner) && "supabase-dev".equals(field(p,"environment")), "OWNER_ENVIRONMENT");
        validateMappings(p);
        String prefix=owner+".", project=field(p,prefix+"project"), mode=field(p,prefix+"mode");
        String url=env(owner.toUpperCase(Locale.ROOT)+"_MIGRATION_DB_URL"), login=env(owner.toUpperCase(Locale.ROOT)+"_MIGRATION_DB_USERNAME");
        URI uri=validateUrl(url,project,mode);
        require(uri.getHost().equals(field(p,prefix+"host")) && login.equals(field(p,prefix+"login")), "CONNECT_MAPPING");
        String sqlUser=field(p,prefix+"sqlUser");
        require(sqlUser.matches("[a-z][a-z0-9_]{2,62}") && !Set.of("postgres","service_role","anon","authenticated").contains(sqlUser), "DEDICATED_MIGRATOR_REQUIRED");
        require(login.equals(sqlUser+(mode.equals("session")?"."+project:"")), "TENANT_LOGIN");
        int major=Integer.parseInt(field(p,prefix+"serverMajor"));
        supportedMajor(major);
        String ca=Arrays.stream(uri.getRawQuery().split("&")).filter(s->s.startsWith("sslrootcert=")).findFirst().orElseThrow().substring(12);
        Path caPath=Path.of(URLDecoder.decode(ca,java.nio.charset.StandardCharsets.UTF_8));
        require(Files.isRegularFile(caPath) && sha(caPath).equals(field(p,prefix+"caSha256")), "CA_HASH");
        return new Target(owner,project,mode,url,login,sqlUser,major,field(p,prefix+"v1Sha256"),p.getProperty(prefix+"inventorySha256",""),p);
    }
    static void validateMappings(Properties p) {
        Set<String> projects=new HashSet<>(), logins=new HashSet<>();
        for(String o:OWNERS) {
            require(projects.add(field(p,o+".project")), "DUPLICATE_PROJECT");
            // Prepare/verify one project at a time; do not require credentials for other projects.
            // The selected owner still requires its exact manifest login and process credentials above.
            String configured=p.getProperty(o+".login","");
            require(configured.isBlank() || logins.add(configured), "DUPLICATE_LOGIN");
        }
    }

    static void supportedMajor(int major) {
        // PostgreSQL 17 support landed in Flyway 10.20; this tool pins both modules at 10.22.
        // Keep newer unaccepted majors blocked rather than equating protocol compatibility with acceptance.
        require(major>=14 && major<=17, "PINNED_FLYWAY_VERSION_REVIEW_REQUIRED");
    }

    static void controlPlane(Target t) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.supabase.com/v1/projects/"+t.project))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+env("BATCH6B_MANAGEMENT_TOKEN")).GET().build();
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
            .send(req,HttpResponse.BodyHandlers.ofString());
        require(response.statusCode()==200, "CONTROL_PLANE_ACCESS");
        JsonNode n=JSON.readTree(response.body());
        require(t.project.equals(n.path("id").asText()) && field(t.manifest,t.owner+".organizationId").equals(n.path("organization_id").asText()), "CONTROL_PLANE_IDENTITY");
        require(("db."+t.project+".supabase.co").equals(n.path("database").path("host").asText()), "CONTROL_PLANE_DATABASE");
    }

    static Object scalar(Connection c,String sql) throws SQLException {
        try(var s=c.createStatement()) { s.setQueryTimeout(20); try(var r=s.executeQuery(sql)) { require(r.next(),"SQL_EMPTY"); return r.getObject(1); } }
    }
    static void identity(Connection c,Target t) throws SQLException {
        identity(c,t,false);
    }
    static void identity(Connection c,Target t,boolean readOnlyPreflight) throws SQLException {
        c.setReadOnly(true); c.setAutoCommit(false);
        try {
            require("postgres".equals(scalar(c,"select current_database()")),"SQL_DATABASE");
            require(t.sqlUser.equals(scalar(c,"select current_user")) && t.sqlUser.equals(scalar(c,"select session_user")),"SQL_ROLE");
            require(Integer.parseInt(scalar(c,"show server_version_num").toString())/10000==t.major,"SQL_VERSION");
            require("public".equals(scalar(c,"select current_schema()")),"SQL_SCHEMA");
            boolean backendTls=Boolean.TRUE.equals(scalar(c,"select ssl from pg_stat_ssl where pid=pg_backend_pid()"));
            if(!backendTls) {
                require(readOnlyPreflight && t.mode.equals("session"),"SQL_BACKEND_TLS_REQUIRED_FOR_MIGRATION");
                System.out.println(t.owner+" POOLER_BACKEND_TLS_FALSE_MIGRATION_BLOCKED");
            }
            reviewedEventTriggers(c,t);
        } finally { c.rollback(); c.setAutoCommit(true); c.setReadOnly(false); }
    }
    record ClientTls(String protocol) {}
    @FunctionalInterface interface ClientTlsVerifier {ClientTls verify(Connection c,Target t) throws Exception;}
    static ClientTls clientTls(Connection c,Target t) throws Exception {
        return new ClientTls(clientTlsSession(c,t).getProtocol());
    }
    static javax.net.ssl.SSLSession clientTlsSession(Connection c,Target t) throws Exception {
        // pg_stat_ssl observes the pooler->server hop. Inspect this process's actual TLS socket
        // for the client hop. This adapter is deliberately pinned to pgJDBC42.7.4 and fails closed
        // if a driver update changes its stream layout. No SSL factory/verifier is replaced.
        var pg=c.unwrap(org.postgresql.jdbc.PgConnection.class);
        var field=org.postgresql.core.QueryExecutorBase.class.getDeclaredField("pgStream");
        require(field.trySetAccessible(),"CLIENT_TLS_PROOF_UNAVAILABLE");
        var stream=(org.postgresql.core.PGStream)field.get(pg.getQueryExecutor());
        require(stream.getSocket() instanceof javax.net.ssl.SSLSocket,"CLIENT_TLS_REQUIRED");
        var socket=(javax.net.ssl.SSLSocket)stream.getSocket();
        var session=socket.getSession();
        String host=URI.create(t.url.substring(5)).getHost();
        require(session.isValid() && Set.of("TLSv1.2","TLSv1.3").contains(session.getProtocol())
            && host.equalsIgnoreCase(session.getPeerHost()) && session.getPeerCertificates().length>0,"CLIENT_TLS_PROOF_REJECTED");
        // A successful connection used the strict verified URL/CA from target(); pgJDBC's
        // standard LibPQFactory and hostname verifier remain unchanged.
        return session;
    }

    // Every physical Flyway connection is verified before the library receives it.
    static final class GuardedSource implements DataSource {
        final Target target; final DataSource delegate; final boolean readOnlyPreflight; final ClientTlsVerifier verifier;
        GuardedSource(Target t,DataSource d){this(t,d,false,HostedRunner::clientTls);}
        GuardedSource(Target t,DataSource d,boolean preflight){this(t,d,preflight,HostedRunner::clientTls);}
        // Dependency injection for synthetic unit connections only; CLI always uses the adapter above.
        GuardedSource(Target t,DataSource d,boolean preflight,ClientTlsVerifier v){target=t;delegate=d;readOnlyPreflight=preflight;verifier=v;}
        public Connection getConnection() throws SQLException {
            Connection c=delegate.getConnection();
            try {
                ClientTls tls=verifier.verify(c,target);
                identity(c,target,readOnlyPreflight);
                System.out.println(target.owner+" CLIENT_TLS_VERIFIED protocol="+tls.protocol());
                return c;
            } catch(Exception ex) {
                c.close();
                String code=ex instanceof IllegalStateException && ex.getMessage()!=null && ex.getMessage().matches("[A-Z0-9_]{1,100}")
                    ?ex.getMessage():ex instanceof SQLException sql && sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}")
                    ?"SQLSTATE_"+sql.getSQLState():"REJECTED";
                throw new SQLException("CONNECTION_GUARD_"+code);
            }
        }
        public Connection getConnection(String u,String p) throws SQLException { throw new SQLException("NO_CREDENTIAL_OVERRIDE"); }
        public PrintWriter getLogWriter(){return null;} public void setLogWriter(PrintWriter p){throw new UnsupportedOperationException();}
        public void setLoginTimeout(int seconds){throw new UnsupportedOperationException();} public int getLoginTimeout(){return 10;}
        public Logger getParentLogger(){return Logger.getLogger("checkinn.runner");}
        public <T>T unwrap(Class<T> c) throws SQLException{throw new SQLException("NO_UNWRAP");} public boolean isWrapperFor(Class<?> c){return false;}
    }

    // Canonicalized restricted catalog: names/ACLs/bodies remain in memory; publish only SHA/counts.
    static final List<String> INVENTORY = List.of(
        "select nspname,pg_get_userbyid(nspowner),nspacl::text from pg_namespace where nspname='public' order by 1",
        "select c.relname,c.relkind,pg_get_userbyid(c.relowner),c.relacl::text,c.relrowsecurity,c.relforcerowsecurity,e.extname from pg_class c join pg_namespace n on n.oid=c.relnamespace left join pg_depend d on d.classid='pg_class'::regclass and d.objid=c.oid and d.deptype='e' left join pg_extension e on e.oid=d.refobjid where n.nspname='public' order by 1,2",
        "select p.oid::regprocedure::text,pg_get_userbyid(p.proowner),p.proacl::text,p.prosecdef,p.proconfig::text,p.prokind,p.prosrc,p.probin,case when p.prokind<>'a' then pg_get_functiondef(p.oid) end from pg_proc p where p.pronamespace='public'::regnamespace order by 1",
        "select typname,typtype,pg_get_userbyid(typowner),typacl::text from pg_type where typnamespace='public'::regnamespace order by 1",
        "select c.relname,t.tgname,t.tgisinternal,pg_get_triggerdef(t.oid) from pg_trigger t join pg_class c on c.oid=t.tgrelid where c.relnamespace='public'::regnamespace order by 1,2",
        "select * from pg_policies where schemaname='public' order by tablename,policyname",
        "select extname,extversion,extnamespace::regnamespace::text from pg_extension order by 1",
        "select rolname,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolbypassrls,rolconfig::text from pg_roles order by 1",
        "select roleid::regrole::text,member::regrole::text,grantor::regrole::text,admin_option from pg_auth_members order by 1,2,3",
        "select defaclrole::regrole::text,defaclnamespace::regnamespace::text,defaclobjtype,defaclacl::text from pg_default_acl order by 1,2,3",
        "select table_name,column_name,grantee,privilege_type from information_schema.column_privileges where table_schema='public' order by 1,2,3,4",
        "select current_setting('max_connections'),current_setting('superuser_reserved_connections')",
        "select c.relname,a.attname,a.attnum,format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attidentity,pg_get_expr(d.adbin,d.adrelid),a.attacl::text from pg_attribute a join pg_class c on c.oid=a.attrelid left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where c.relnamespace='public'::regnamespace and a.attnum>0 and not a.attisdropped order by 1,3",
        "select conrelid::regclass::text,conname,contype,convalidated,pg_get_constraintdef(oid) from pg_constraint where connamespace='public'::regnamespace order by 1,2",
        "select tablename,indexname,indexdef from pg_indexes where schemaname='public' order by 1,2",
        "select sequencename,sequenceowner,data_type,start_value,min_value,max_value,increment_by,cycle,cache_size from pg_sequences where schemaname='public' order by 1",
        "select evtname,evtevent,evtowner::regrole::text,evtfoid::regprocedure::text,evtenabled,evttags::text from pg_event_trigger order by 1"
    );
    static String inventory(Connection c) throws Exception {
        List<Object> rows=new ArrayList<>();
        for(String sql:INVENTORY) try(var s=c.createStatement()) {
            s.setQueryTimeout(20); try(var r=s.executeQuery(sql)) {
                List<Object> result=new ArrayList<>();
                while(r.next()) { List<String> row=new ArrayList<>(); for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.add(r.getString(i)); result.add(row); }
                rows.add(result);
            }
        }
        return sha(JSON.writeValueAsBytes(rows));
    }
    static void cleanBusinessSchema(Connection c) throws SQLException {
        cleanBusinessObjects(c);
        require(((Number)scalar(c,"select count(*) from pg_event_trigger where evtenabled<>'D'")).longValue()==0,"EVENT_TRIGGERS_REQUIRE_REVIEW");
    }
    static void cleanBusinessSchema(Connection c,Target t) throws Exception {
        cleanBusinessObjects(c);
        reviewedEventTriggers(c,t);
    }
    static void cleanBusinessObjects(Connection c) throws SQLException {
        require(scalar(c,"select to_regclass('public.flyway_schema_history')")==null,"EXISTING_HISTORY_STOP");
        // Supabase-managed extension members allowed, arbitrary user objects never auto-allowed.
        for(String catalog:List.of("pg_class","pg_proc","pg_type")) {
            String ns=catalog.equals("pg_class")?"relnamespace":catalog.equals("pg_proc")?"pronamespace":"typnamespace";
            String filter=catalog.equals("pg_type")?" and t.typtype<>'p'":"";
            require(((Number)scalar(c,"select count(*) from "+catalog+" t where "+ns+"='public'::regnamespace"+filter+" and not exists(select 1 from pg_depend d where d.classid='"+catalog+"'::regclass and d.objid=t.oid and d.deptype='e')")).longValue()==0,"UNEXPECTED_PUBLIC_OBJECTS_STOP");
        }
    }
    static final Map<String,String> PLATFORM_EVENTS=Map.of(
        "issue_graphql_placeholder","set_graphql_placeholder",
        "issue_pg_cron_access","grant_pg_cron_access",
        "issue_pg_graphql_access","grant_pg_graphql_access",
        "issue_pg_net_access","grant_pg_net_access",
        "pgrst_ddl_watch","pgrst_ddl_watch",
        "pgrst_drop_watch","pgrst_drop_watch");
    static final String EVENT_SQL="select t.evtname as name,t.evtevent as event,t.evttags as tags,t.evtenabled as enabled,pg_get_userbyid(t.evtowner) as \"triggerOwner\",n.nspname as \"functionSchema\",p.proname as \"functionName\",pg_get_function_identity_arguments(p.oid) as \"functionIdentityArguments\",pg_get_userbyid(p.proowner) as \"functionOwner\",l.lanname as language,p.prosecdef as \"securityDefiner\",p.proconfig as config,p.proacl::text as \"functionAcl\",pg_get_functiondef(p.oid) as definition,(select e.extname from pg_depend d join pg_extension e on e.oid=d.refobjid where d.classid='pg_proc'::regclass and d.objid=p.oid and d.deptype='e' limit 1) as extension from pg_event_trigger t join pg_proc p on p.oid=t.evtfoid join pg_namespace n on n.oid=p.pronamespace join pg_language l on l.oid=p.prolang order by t.evtname";
    static JsonNode eventDescriptors(Connection c) throws SQLException {
        var array=JSON.createArrayNode();
        try(var s=c.createStatement()) {
            s.setQueryTimeout(20);
            try(var rows=s.executeQuery(EVENT_SQL)) {
                while(rows.next()) {
                    var row=JSON.createObjectNode();
                    for(String key:List.of("name","event","enabled","triggerOwner","functionSchema","functionName","functionIdentityArguments","functionOwner","language","functionAcl","definition","extension")) row.put(key,rows.getString(key));
                    row.put("securityDefiner",rows.getBoolean("securityDefiner"));
                    for(String key:List.of("tags","config")) {
                        java.sql.Array a=rows.getArray(key);
                        try {row.set(key,a==null?JSON.nullNode():JSON.valueToTree(a.getArray()));}
                        finally {if(a!=null)a.free();}
                    }
                    array.add(row);
                }
            }
        }
        return array;
    }
    static JsonNode checkedEventReview(Target t,Path file) throws Exception {
        require(sha(file).equals(field(t.manifest,t.owner+".eventTriggerReviewSha256")),"EVENT_REVIEW_HASH");
        JsonNode review=JSON.readTree(file.toFile());
        require(review.path("formatVersion").asInt()==1 && "STATIC_REVIEWED_NOT_MIGRATION_APPROVAL".equals(review.path("status").asText()),"EVENT_REVIEW_STATUS");
        require(t.owner.equals(review.path("owner").asText()) && t.project.equals(review.path("project").asText())
            && t.major==review.path("serverMajor").asInt() && t.migrationSha.equals(review.path("v1Sha256").asText()),"EVENT_REVIEW_SCOPE");
        JsonNode expected=review.path("eventTriggers");
        require(expected.isArray() && expected.size()==6,"EVENT_REVIEW_SET");
        Set<String> names=new HashSet<>();
        for(JsonNode event:expected) {
            String name=event.path("name").asText();
            require(PLATFORM_EVENTS.containsKey(name) && names.add(name) && PLATFORM_EVENTS.get(name).equals(event.path("functionName").asText()),"EVENT_REVIEW_SET");
            require("supabase_admin".equals(event.path("triggerOwner").asText()) && "supabase_admin".equals(event.path("functionOwner").asText())
                && "extensions".equals(event.path("functionSchema").asText()) && "plpgsql".equals(event.path("language").asText())
                && event.path("securityDefiner").isBoolean() && !event.path("securityDefiner").asBoolean()
                && "O".equals(event.path("enabled").asText()) && event.path("functionIdentityArguments").asText("missing").isEmpty()
                && event.path("extension").isNull() && !event.path("definition").asText().isBlank()
                && event.path("config").equals(JSON.valueToTree(List.of("search_path=\"\""))),"EVENT_REVIEW_ATTRIBUTES");
            if(name.startsWith("issue_")) {
                String tag=name.equals("issue_graphql_placeholder")?"DROP EXTENSION":"CREATE EXTENSION";
                require(event.path("tags").equals(JSON.valueToTree(List.of(tag)))
                    && (name.equals("issue_graphql_placeholder")?"sql_drop":"ddl_command_end").equals(event.path("event").asText()),"EVENT_REVIEW_TAGS");
            } else require(event.path("tags").isNull() && (name.equals("pgrst_drop_watch")?"sql_drop":"ddl_command_end").equals(event.path("event").asText()),"EVENT_REVIEW_TAGS");
        }
        return expected;
    }
    static void reviewedEventTriggers(Connection c,Target t) throws SQLException {
        String path=t.manifest.getProperty(t.owner+".eventTriggerReviewFile","");
        if(path.isBlank()) {
            require(!t.manifest.containsKey(t.owner+".eventTriggerReviewSha256"),"EVENT_REVIEW_INCOMPLETE");
            require(((Number)scalar(c,"select count(*) from pg_event_trigger where evtenabled<>'D'")).longValue()==0,"EVENT_TRIGGERS_REQUIRE_REVIEW");
            return;
        }
        try {
            JsonNode expected=checkedEventReview(t,Path.of(path).toRealPath());
            JsonNode actual=eventDescriptors(c);
            // Sort by name so object-key/array export ordering does not create an accidental exemption.
            Map<String,JsonNode> a=new TreeMap<>(),b=new TreeMap<>();
            for(JsonNode event:expected)a.put(event.path("name").asText(),event);
            for(JsonNode event:actual)b.put(event.path("name").asText(),event);
            require(a.equals(b),"EVENT_TRIGGER_DRIFT_STOP");
        } catch(IllegalStateException ex) {throw ex;}
        catch(Exception ex) {throw new SQLException("EVENT_TRIGGER_REVIEW_REJECTED");}
    }
    static void security(Connection c,Target t) throws SQLException {
        String runtime=field(t.manifest,t.owner+".runtimeRole");
        require(runtime.matches("[a-z][a-z0-9_]{2,62}") && !runtime.equals(t.sqlUser),"RUNTIME_ROLE");
        require(Boolean.TRUE.equals(scalar(c,"select has_schema_privilege(current_user,'public','USAGE') and has_schema_privilege(current_user,'public','CREATE')")),"MIGRATION_PERMISSIONS");
        require(Boolean.FALSE.equals(scalar(c,"select has_schema_privilege('"+runtime+"','public','CREATE') or pg_has_role('"+runtime+"',current_user,'MEMBER')")),"RUNTIME_DDL_MEMBERSHIP");
        require(((Number)scalar(c,"select count(*) from pg_roles where pg_has_role('"+runtime+"',oid,'MEMBER') and (rolsuper or rolcreaterole or rolcreatedb or rolbypassrls)")).longValue()==0,"RUNTIME_ESCALATION");
        require(((Number)scalar(c,"select count(*) from pg_namespace n cross join lateral aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a where n.nspname='public' and a.grantee=0 and a.privilege_type='CREATE'")).longValue()==0,"PUBLIC_SCHEMA_CREATE");
        // Global defaults plus schema-specific additions. Inherited API grants also reject.
        String defaults="with a as (select x.* from (values ('r'::\"char\"),('S'::\"char\"),('f'::\"char\")) k(kind) cross join lateral aclexplode(coalesce((select defaclacl from pg_default_acl where defaclrole=current_user::regrole and defaclnamespace=0 and defaclobjtype=k.kind),acldefault(k.kind,current_user::regrole))) x union all select x.* from pg_default_acl d cross join lateral aclexplode(d.defaclacl) x where d.defaclrole=current_user::regrole and d.defaclnamespace='public'::regnamespace) select count(*) from a where grantee=0 or exists(select 1 from pg_roles r where r.rolname in ('anon','authenticated','service_role') and (r.oid=a.grantee or pg_has_role(r.oid,a.grantee,'MEMBER')))";
        require(((Number)scalar(c,defaults)).longValue()==0,"UNSAFE_CREATOR_DEFAULT_ACL");
    }
    static void approval(Target t,Path manifest,Path migration) throws Exception {
        Properties a=new Properties(); try(var in=Files.newInputStream(Path.of(env("BATCH6B_APPROVAL_FILE")))) { a.load(in); }
        validateApproval(t,manifest,migration,a);
    }
    static void validateApproval(Target t,Path manifest,Path migration,Properties a) throws Exception {
        require("canonical-v1".equals(field(a,"operation")) && "supabase-dev".equals(field(a,"environment")),"APPROVAL_SCOPE");
        require(field(a,"manifestSha256").equals(sha(manifest)) && field(a,"service").equals(t.owner) && field(a,"project").equals(t.project),"APPROVAL_TARGET");
        require(field(a,"v1Sha256").equals(sha(migration)) && field(a,"inventorySha256").equals(t.inventorySha),"APPROVAL_HASH");
        require(Instant.parse(field(a,"expiresAt")).isAfter(Instant.now()) && Instant.parse(field(a,"expiresAt")).isBefore(Instant.now().plus(Duration.ofHours(24))),"APPROVAL_EXPIRY");
        for(String key:List.of("mappingApproved","migrationApproved","securityReviewed","dataApiDisabledOrPublicUnexposed","exclusiveDdlWindow","appsStopped")) require("true".equals(field(a,key)),"APPROVAL_GATE");
        require(!field(a,"approvedBy").isBlank(),"APPROVER");
    }

    static void run(String[] args) throws Exception {
        require(args.length==2 && Set.of("preflight","migrate").contains(args[0]),"USAGE_PREFLIGHT_OR_MIGRATE_OWNER");
        Path manifest=Path.of(env("BATCH6B_TARGET_MANIFEST")).toRealPath();
        Properties p=new Properties(); try(var in=Files.newInputStream(manifest)){p.load(in);}
        Target t=target(p,args[1]);
        Path root=Path.of(env("BATCH6B_BACKEND_ROOT")).toRealPath();
        Path dir=root.resolve(t.owner+"-service/src/main/resources/db/canonical").toRealPath();
        Path v1=dir.resolve("V1__canonical_schema.sql");
        try(var files=Files.list(dir)){require(files.toList().equals(List.of(v1)),"CANONICAL_LOCATION_ONLY");}
        require(sha(v1).equals(t.migrationSha),"V1_HASH");
        if(args[0].equals("migrate")) approval(t,manifest,v1);
        controlPlane(t);
        System.out.println(t.owner+" CONTROL_PLANE_IDENTITY_PASS");
        PGSimpleDataSource raw=new PGSimpleDataSource(); raw.setUrl(t.url); raw.setUser(t.login); raw.setPassword(env(t.owner.toUpperCase(Locale.ROOT)+"_MIGRATION_DB_PASSWORD"));
        GuardedSource ds=new GuardedSource(t,raw,args[0].equals("preflight"));
        Path lockDir=root.resolve(".local/batch6b-locks"); Files.createDirectories(lockDir);
        try(var channel=FileChannel.open(lockDir.resolve(t.project+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE); var lock=channel.tryLock()) {
            require(lock!=null,"DEPLOYMENT_MUTEX");
            String hash;
            try(Connection c=ds.getConnection()) {
                c.setReadOnly(true); c.setAutoCommit(false);
                try { cleanBusinessSchema(c,t); hash=inventory(c); security(c,t); }
                finally {c.rollback();}
            }
            System.out.println(t.owner+" PREFLIGHT_SQL_PASS inventorySha256="+hash);
            if(args[0].equals("preflight")) {
                System.out.println("HOSTED_SECURITY_REVIEW_AND_EXACT_MIGRATION_APPROVAL_REQUIRED"); return;
            }
            require(hash.equals(t.inventorySha),"INVENTORY_CHANGED_STOP");
            // Repeat control-plane and fresh catalog immediately before allowing Flyway metadata writes.
            controlPlane(t);
            try(Connection c=ds.getConnection()) {
                c.setReadOnly(true);c.setAutoCommit(false);
                try {cleanBusinessSchema(c,t);security(c,t);require(hash.equals(inventory(c)),"PREFLIGHT_DRIFT");} finally{c.rollback();}
            }
            Flyway f=canonicalFlyway(ds,dir);
            require(f.migrate().migrationsExecuted==1,"V1_MIGRATION_COUNT");
            f.validate();
            try(Connection c=ds.getConnection()) {
                require(((Number)scalar(c,"select count(*) from public.flyway_schema_history")).longValue()==1,"HISTORY_COUNT");
                require(((Number)scalar(c,"select count(*) from public.flyway_schema_history where version='1' and type='SQL' and script='V1__canonical_schema.sql' and success")).longValue()==1,"HISTORY_V1");
            }
            require(f.migrate().migrationsExecuted==0,"RERUN_NOT_NOOP");
            System.out.println(t.owner+" V1_PASS RERUN_NOOP_PASS; catalog/runtime/API acceptance still required");
        }
    }
    static Flyway canonicalFlyway(DataSource source,Path canonicalDirectory) {
        return Flyway.configure().dataSource(source).locations("filesystem:"+canonicalDirectory.toString().replace('\\','/'))
            .schemas("public").defaultSchema("public").createSchemas(false).baselineOnMigrate(false)
            .cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
    }
    public static void main(String[] args) {
        // Libraries can log URLs/SQL exception values; never forward their log output.
        LogManager.getLogManager().reset();
        try {run(args);} catch(Exception ex) {
            String code=safeFailureCode(ex);
            System.err.println("BLOCKED "+code); System.exit(2);
        }
    }
    static String safeFailureCode(Exception ex) {
        if(ex instanceof IllegalStateException && ex.getMessage()!=null && ex.getMessage().matches("[A-Z0-9_]{1,100}"))return ex.getMessage();
        if(ex instanceof SQLException sql) {
            if(sql.getMessage()!=null && sql.getMessage().matches("CONNECTION_GUARD_[A-Z0-9_]{1,110}"))return sql.getMessage();
            if(sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}"))return "SAFE_STOP_SQLSTATE_"+sql.getSQLState();
        }
        return "SAFE_STOP_"+ex.getClass().getSimpleName();
    }
}
