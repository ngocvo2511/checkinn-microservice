package com.checkinn.db;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;

/** Insert-only reference-data operation, separate from sealed synthetic acceptance. */
public final class HostedReferenceSeed {
    public static void main(String[] args) {
        java.util.logging.LogManager.getLogManager().reset();
        try { run(args); }
        catch(Exception ex) { System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2); }
    }
    static void run(String[] args)throws Exception {
        HostedRunner.require(args.length==0,"NO_TARGET_OVERRIDE");
        Properties p=new Properties();
        try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))) {p.load(in);}
        var migration=HostedRunner.target(p,"hotel");HostedRunner.controlPlane(migration);
        String role=HostedRunner.field(p,"hotel.runtimeRole"),url=HostedRunner.env("HOTEL_DB_URL"),login=HostedRunner.env("HOTEL_DB_USERNAME");
        HostedRunner.require(url.equals(migration.url()) && login.equals(role+"."+migration.project()),"RUNTIME_MAPPING");
        var t=new HostedRunner.Target("hotel",migration.project(),migration.mode(),url,login,role,migration.major(),migration.migrationSha(),migration.inventorySha(),p);
        var raw=new PGSimpleDataSource();raw.setUrl(url);raw.setUser(login);raw.setPassword(HostedRunner.env("HOTEL_DB_PASSWORD"));
        String input=new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        try(Connection c=new HostedRunner.GuardedSource(t,raw).getConnection()) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.setQueryTimeout(30);
                s.execute("SET LOCAL lock_timeout='5s'");
                int inserted=0;
                for(String statement:input.split(";")) {
                    if(statement.isBlank())continue;
                    HostedRunner.require(statement.matches("(?s)\\s*INSERT INTO public\\.(?:provinces|cities) .+ON CONFLICT \\(name\\) DO NOTHING\\s*"),"INSERT_ONLY_REFERENCE_TABLES");
                    inserted+=s.executeUpdate(statement);
                }
                c.commit();System.out.println("REFERENCE_ROWS_INSERTED "+inserted);
            }catch(Exception ex) {c.rollback();throw ex;}
        }
    }
}
