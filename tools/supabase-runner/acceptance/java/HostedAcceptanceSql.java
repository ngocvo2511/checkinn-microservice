package com.checkinn.db;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;
public final class HostedAcceptanceSql {
  static void run(String[] args)throws Exception {
    HostedRunner.require(args.length==2 && Set.of("readonly","fixture").contains(args[1]),"SQL_MODE");
    String owner=args[0],prefix=owner.toUpperCase(Locale.ROOT);
    Properties p=new Properties();try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))){p.load(in);}
    var migration=HostedRunner.target(p,owner);HostedRunner.controlPlane(migration);
    String role=HostedRunner.field(p,owner+".runtimeRole"),url=HostedRunner.env(prefix+"_DB_URL"),login=HostedRunner.env(prefix+"_DB_USERNAME");
    HostedRunner.require(url.equals(migration.url()) && login.equals(role+"."+migration.project()),"RUNTIME_MAPPING");
    var t=new HostedRunner.Target(owner,migration.project(),migration.mode(),url,login,role,migration.major(),migration.migrationSha(),migration.inventorySha(),p);
    var ds=new PGSimpleDataSource();ds.setUrl(url);ds.setUser(login);ds.setPassword(HostedRunner.env(prefix+"_DB_PASSWORD"));
    String sql=new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    HostedRunner.require(!sql.isBlank() && !java.util.regex.Pattern.compile("\\b(?:CREATE|ALTER|DROP|TRUNCATE|DELETE|GRANT|REVOKE|COPY|CALL)\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sql).find(),"ACCEPTANCE_DML_ONLY");
    HostedRunner.require(!java.util.regex.Pattern.compile("(?:^|;)\\s*DO\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sql).find(),"NO_PROCEDURAL_FIXTURE_SQL");
    try(var c=new HostedRunner.GuardedSource(t,ds).getConnection()) {
      c.setReadOnly(args[1].equals("readonly"));c.setAutoCommit(false);
      try(var s=c.createStatement()) {
        s.setQueryTimeout(30);boolean result=s.execute(sql);
        do {
          if(result)try(var rows=s.getResultSet()) {
            while(rows.next()) {
              String value=rows.getString(1);
              System.out.println("RESULT_BASE64 "+Base64.getEncoder().encodeToString(Objects.toString(value,"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
          }
          result=s.getMoreResults();
        }while(result || s.getUpdateCount()!=-1);
        if(args[1].equals("fixture"))c.commit();else c.rollback();
      }catch(Exception ex){c.rollback();throw ex;}
    }
  }
  public static void main(String[] args){java.util.logging.LogManager.getLogManager().reset();try{run(args);}catch(Exception ex){System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2);}}
}
