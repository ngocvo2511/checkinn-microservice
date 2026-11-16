package com.checkinn.db;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.zaxxer.hikari.*;
import org.postgresql.ds.PGSimpleDataSource;
public final class HostedPoolProbe {
  static void run(String owner)throws Exception {
    Path root=Path.of(HostedRunner.env("BATCH6B_BACKEND_ROOT"));Properties p=new Properties();
    try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))){p.load(in);}
    var migration=HostedRunner.target(p,owner);HostedRunner.controlPlane(migration);
    String prefix=owner.toUpperCase(Locale.ROOT),role=HostedRunner.field(p,owner+".runtimeRole"),url=HostedRunner.env(prefix+"_DB_URL"),login=HostedRunner.env(prefix+"_DB_USERNAME");
    HostedRunner.require(url.equals(migration.url()) && login.equals(role+"."+migration.project()),"APP_MAPPING");
    var t=new HostedRunner.Target(owner,migration.project(),migration.mode(),url,login,role,migration.major(),migration.migrationSha(),migration.inventorySha(),p);
    var raw=new PGSimpleDataSource();raw.setUrl(url);raw.setUser(login);raw.setPassword(HostedRunner.env(prefix+"_DB_PASSWORD"));
    HikariConfig config=new HikariConfig();config.setDataSource(new HostedRunner.GuardedSource(t,raw));
    config.setMaximumPoolSize(2);config.setMinimumIdle(1);config.setConnectionTimeout(10000);config.setValidationTimeout(1000);
    config.setInitializationFailTimeout(10000);config.setPoolName("batch6b-"+owner);config.setConnectionInitSql("SET TIME ZONE 'UTC'");
    Map<String,Object> report=new LinkedHashMap<>();report.put("owner",owner);
    try(var pool=new HikariDataSource(config)) {
      try(Connection a=pool.getConnection();Connection b=pool.getConnection()) {
        int maximum=((Number)HostedRunner.scalar(a,"select setting::integer from pg_settings where name='max_connections'")).intValue();
        int reserved=((Number)HostedRunner.scalar(a,"select setting::integer from pg_settings where name='superuser_reserved_connections'")).intValue();
        int cap=((Number)HostedRunner.scalar(a,"select rolconnlimit from pg_roles where rolname=current_user")).intValue();
        int sessions=((Number)HostedRunner.scalar(a,"select count(*) from pg_stat_activity")).intValue();
        HostedRunner.require(maximum-reserved>=7 && cap>=2 && sessions+3<maximum-reserved,"BUDGET_HEADROOM");
        report.put("sqlMaxConnections",maximum);report.put("sqlSuperuserReserved",reserved);report.put("runtimeRoleCap",cap);report.put("observedDatabaseSessions",sessions);
        pool.setConnectionTimeout(1500);
        boolean timeout=false;long start=System.nanoTime();
        try(var forbidden=pool.getConnection()){}catch(SQLTransientConnectionException expected){timeout=true;}
        pool.setConnectionTimeout(10000);
        HostedRunner.require(timeout,"POOL_EXHAUSTION_TIMEOUT_REQUIRED");report.put("exhaustionTimeoutMillis",(System.nanoTime()-start)/1000000);
        HostedRunner.require(((Number)HostedRunner.scalar(a,"select 1")).intValue()==1 && ((Number)HostedRunner.scalar(b,"select 1")).intValue()==1,"POOL_TWO_CONNECTIONS");
      }
      try(var recovered=pool.getConnection()){HostedRunner.require(((Number)HostedRunner.scalar(recovered,"select 1")).intValue()==1,"POOL_RELEASE_RECOVERY");}
      // Abort this process's own JDBC connection only; do not kill DB sessions or change network/SSL.
      Connection interrupted=pool.getConnection();
      interrupted.unwrap(org.postgresql.jdbc.PgConnection.class).abort(Runnable::run);
      try {interrupted.close();} catch(SQLException expectedClosed) {HostedRunner.require("08003".equals(expectedClosed.getSQLState()),"ABORT_CLOSE_SQLSTATE");}
      pool.setConnectionTimeout(10000);
      pool.getHikariPoolMXBean().softEvictConnections();
      try(var a=pool.getConnection();var b=pool.getConnection()) {
        HostedRunner.require(((Number)HostedRunner.scalar(a,"select 1")).intValue()==1 && ((Number)HostedRunner.scalar(b,"select 1")).intValue()==1,"CLIENT_ABORT_RECOVERY");
      }
      report.put("maximumPoolSize",2);report.put("minimumIdle",1);report.put("allocatedProjectBudget",7);report.put("migrationReserve",2);report.put("operationsReserve",3);
      report.put("startupTwoConnections","PASS");report.put("exhaustionAndReleaseRecovery","PASS");report.put("localClientAbortAndFreshConnectionRecovery","PASS");
      report.put("providerWideNetworkOutage","NOT SIMULATED");report.put("businessDmlPerformed",false);report.put("status","PASS");
    }
    Files.writeString(root.resolve(".local/batch6b/"+owner+".pool-verification.json"),HostedRunner.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
    System.out.println(owner+" HIKARI_BUDGET_EXHAUSTION_RECOVERY_PASS");
  }
  public static void main(String[] args){java.util.logging.LogManager.getLogManager().reset();try{HostedRunner.require(args.length==1,"OWNER");run(args[0]);}catch(Exception ex){System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2);}}
}
