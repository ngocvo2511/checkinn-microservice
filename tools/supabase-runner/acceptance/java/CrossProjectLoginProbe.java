package com.checkinn.db;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;
public final class CrossProjectLoginProbe {
  static void run(String[] args)throws Exception {
    HostedRunner.require(args.length==2 && HostedRunner.OWNERS.containsAll(List.of(args[0],args[1])) && !args[0].equals(args[1]),"DISTINCT_OWNERS");
    Properties p=new Properties();try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))){p.load(in);}
    var target=HostedRunner.target(p,args[1]);HostedRunner.controlPlane(target);
    String foreign=HostedRunner.field(p,args[0]+".runtimeRole");
    HostedRunner.require(!foreign.equals(HostedRunner.field(p,args[1]+".runtimeRole")),"DISTINCT_ROLES");
    var own=new PGSimpleDataSource();own.setUrl(target.url());own.setUser(target.login());
    own.setPassword(HostedRunner.env(args[1].toUpperCase(Locale.ROOT)+"_MIGRATION_DB_PASSWORD"));
    try(var c=new HostedRunner.GuardedSource(target,own).getConnection()) {
      c.setReadOnly(true);c.setAutoCommit(false);
      try{HostedRunner.require(((Number)HostedRunner.scalar(c,"select count(*) from pg_roles where rolname='"+foreign+"'")).intValue()==0,"FOREIGN_ROLE_MUST_BE_ABSENT");}
      finally{c.rollback();}
    }
    var ds=new PGSimpleDataSource();ds.setUrl(target.url());ds.setUser(foreign+"."+target.project());
    ds.setPassword(HostedRunner.env("PROBE_FOREIGN_RUNTIME_PASSWORD"));
    boolean rejected=false;
    try(var unexpected=ds.getConnection()){}
    catch(SQLException ex){
      String state=Objects.toString(ex.getSQLState(),"");String message=Objects.toString(ex.getMessage(),"");
      Map<String,Object> diag=new LinkedHashMap<>();diag.put("sqlState",state);
      for(String word:List.of("authentication","password","role","user","not found","does not exist","circuit","upstream","connect","tenant","timeout","pool","query","error"))diag.put(word,message.toLowerCase(Locale.ROOT).contains(word));
      Files.writeString(Path.of(HostedRunner.env("BATCH6B_BACKEND_ROOT")).resolve(".local/batch6b/cross-login-diagnostic.json"),HostedRunner.JSON.writeValueAsString(diag));
      boolean missingRole=message.matches("(?is).*role[^\\n]*does not exist.*");
      HostedRunner.require(Set.of("28P01","28000").contains(state) || message.toLowerCase(Locale.ROOT).contains("user not found") || missingRole || (state.equals("XX000") && message.toLowerCase(Locale.ROOT).contains("authentication error")),"UNEXPECTED_LOGIN_PROBE_FAILURE_"+state);
      rejected=true;
    }
    HostedRunner.require(rejected,"CROSS_PROJECT_LOGIN_UNEXPECTEDLY_ALLOWED");
    System.out.println(args[0]+" TO "+args[1]+" CROSS_PROJECT_RUNTIME_LOGIN_REJECTED");
  }
  public static void main(String[] args){java.util.logging.LogManager.getLogManager().reset();try{run(args);}catch(Exception ex){System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2);}}
}
