package com.checkinn.db;

import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.LogManager;

/** Separate read-only authentication/permissions probe; no JPA, Flyway or business SQL. */
public final class RuntimeReadOnlyProbe {
    static void run(String[] args) throws Exception {
        HostedRunner.require(args.length==1 && HostedRunner.OWNERS.contains(args[0]),"RUNTIME_OWNER");
        String owner=args[0],prefix=owner.toUpperCase(Locale.ROOT);
        Properties p=new Properties();
        try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))){p.load(in);}
        var migration=HostedRunner.target(p,owner);
        String role=HostedRunner.field(p,owner+".runtimeRole");
        HostedRunner.require(role.matches("[a-z][a-z0-9_]{2,62}") && !role.equals(migration.sqlUser())
            && !Set.of("postgres","anon","authenticated","service_role").contains(role),"RUNTIME_ROLE");
        String url=HostedRunner.env(prefix+"_DB_URL"),login=HostedRunner.env(prefix+"_DB_USERNAME");
        HostedRunner.require(url.equals(migration.url()) && login.equals(role+(migration.mode().equals("session")?"."+migration.project():"")),"RUNTIME_CONNECT_MAPPING");
        HostedRunner.controlPlane(migration);
        System.out.println(owner+" CONTROL_PLANE_IDENTITY_PASS");
        var target=new HostedRunner.Target(owner,migration.project(),migration.mode(),url,login,role,migration.major(),migration.migrationSha(),migration.inventorySha(),p);
        var raw=new PGSimpleDataSource();raw.setUrl(url);raw.setUser(login);raw.setPassword(HostedRunner.env(prefix+"_DB_PASSWORD"));
        var source=new HostedRunner.GuardedSource(target,raw,true);
        try(Connection c=source.getConnection()) {
            c.setReadOnly(true);c.setAutoCommit(false);
            try {
                HostedRunner.require(Boolean.TRUE.equals(HostedRunner.scalar(c,"select rolcanlogin and not rolsuper and not rolcreatedb and not rolcreaterole and not rolbypassrls from pg_roles where rolname=current_user")),"RUNTIME_FLAGS");
                HostedRunner.require(Boolean.FALSE.equals(HostedRunner.scalar(c,"select has_schema_privilege(current_user,'public','CREATE')")),"RUNTIME_PUBLIC_DDL");
                HostedRunner.require(Boolean.FALSE.equals(HostedRunner.scalar(c,"select pg_has_role(current_user,'"+migration.sqlUser()+"','MEMBER')")),"RUNTIME_MIGRATOR_MEMBERSHIP");
                HostedRunner.require(((Number)HostedRunner.scalar(c,"select count(*) from pg_class where relnamespace='public'::regnamespace and relowner=current_user::regrole")).longValue()==0,"RUNTIME_PUBLIC_OWNERSHIP");
                boolean temp=Boolean.TRUE.equals(HostedRunner.scalar(c,"select has_database_privilege(current_user,current_database(),'TEMP')"));
                System.out.println(owner+" RUNTIME_READONLY_PASS inheritedDatabaseTemp="+temp);
            } finally {c.rollback();}
        }
    }
    public static void main(String[] args) {
        LogManager.getLogManager().reset();
        try {run(args);}catch(Exception ex){System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2);}
    }
}
