package com.checkinn.db;

import org.postgresql.ds.PGSimpleDataSource;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.LogManager;

/** Two bounded TLS-negative handshakes to the approved endpoint; never executes SQL or Flyway. */
public final class TlsNegativeProbe {
    static String rejection(String url,HostedRunner.Target t,String password,boolean wrongCa) {
        var ds=new PGSimpleDataSource();ds.setUrl(url);ds.setUser(t.login());ds.setPassword(password);
        try(Connection ignored=ds.getConnection()) {return "UNEXPECTED_SUCCESS";}
        catch(Exception ex) {
            for(Throwable cause=ex;cause!=null;cause=cause.getCause()) {
                if(wrongCa && (cause instanceof java.security.cert.CertificateException || cause instanceof java.security.cert.CertPathValidatorException || cause instanceof java.security.cert.CertPathBuilderException))return "CERTIFICATE_REJECTED";
                String message=Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT);
                if(!wrongCa && (cause instanceof javax.net.ssl.SSLPeerUnverifiedException || (message.contains("hostname") && message.contains("could not be verified"))))return "HOSTNAME_REJECTED";
            }
            return "REJECTED_UNCLASSIFIED";
        }
    }
    static void run(String[] args) throws Exception {
        HostedRunner.require(args.length==2 && HostedRunner.OWNERS.contains(args[0]),"TLS_NEGATIVE_ARGUMENTS");
        String owner=args[0];Path wrongCa=Path.of(args[1]).toRealPath();
        Path root=Path.of(HostedRunner.env("BATCH6B_BACKEND_ROOT")).toRealPath();
        HostedRunner.require(wrongCa.startsWith(root.resolve(".local/batch6b")),"TLS_NEGATIVE_CA_LOCATION");
        Properties p=new Properties();try(var in=Files.newInputStream(Path.of(HostedRunner.env("BATCH6B_TARGET_MANIFEST")))){p.load(in);}
        var t=HostedRunner.target(p,owner);HostedRunner.controlPlane(t);
        HostedRunner.require(!HostedRunner.sha(wrongCa).equals(HostedRunner.field(p,owner+".caSha256")),"TLS_NEGATIVE_CA_MUST_DIFFER");
        // Must be a valid, distinct X509 certificate: file/PEM parsing failure is not trust-chain proof.
        try(var in=Files.newInputStream(wrongCa)){java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);}
        String password=HostedRunner.env(owner.toUpperCase(Locale.ROOT)+"_MIGRATION_DB_PASSWORD");
        String badCa=t.url().replaceFirst("sslrootcert=[^&]+","sslrootcert="+java.util.regex.Matcher.quoteReplacement(URLEncoder.encode(wrongCa.toString(),StandardCharsets.UTF_8)));
        HostedRunner.require(!badCa.equals(t.url()) && badCa.contains("sslmode=verify-full"),"TLS_NEGATIVE_URL");
        String caResult=rejection(badCa,t,password,true);
        System.out.println(owner+" TLS_NEGATIVE_CA_"+caResult);
        HostedRunner.require(caResult.equals("CERTIFICATE_REJECTED"),"TLS_NEGATIVE_CA_NOT_PROVEN");
        var ds=new PGSimpleDataSource();ds.setUrl(t.url());ds.setUser(t.login());ds.setPassword(password);
        try(Connection c=ds.getConnection()) {
            var session=HostedRunner.clientTlsSession(c,t);
            var verifier=org.postgresql.ssl.PGjdbcHostnameVerifier.INSTANCE;
            HostedRunner.require(verifier.verify(URI.create(t.url().substring(5)).getHost(),session),"TLS_EXPECTED_HOST_NOT_VERIFIED");
            // Verify a deliberately wrong name against the real peer certificate using the same
            // pinned driver's DEFAULT verifier. No alternate remote host is contacted, no custom
            // verifier is installed and no DNS/hosts/URL override survives this read-only probe.
            HostedRunner.require(!verifier.verify("checkinn-invalid-host.invalid",session),"TLS_NEGATIVE_HOST_NOT_PROVEN");
        }
        System.out.println(owner+" TLS_NEGATIVE_HOST_HOSTNAME_REJECTED");
    }
    public static void main(String[] args) {
        LogManager.getLogManager().reset();try{run(args);}catch(Exception ex){System.err.println("BLOCKED "+HostedRunner.safeFailureCode(ex));System.exit(2);}
    }
}
