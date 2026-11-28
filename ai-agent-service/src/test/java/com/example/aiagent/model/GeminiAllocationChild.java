package com.example.aiagent.model;

import java.math.BigDecimal;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Controlled child JVM: no credentials, provider transport or domain clients. */
@SuppressWarnings("removal")
public final class GeminiAllocationChild {
    public static void main(String[] args) throws Exception {
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission p) { if(p.getName().startsWith("getenv.")) throw new SecurityException("No environment reads"); }
            @Override public void checkConnect(String host,int port) { throw new SecurityException("No network"); }
        });
        var limits=GeminiOperatorApproval.JSON.readValue(Files.readAllBytes(Path.of(args[0])),GeminiSharedAllocation.Limits.class);
        String campaign=args[2]; String session=args.length>4?args[4]:campaign+"_SESSION"; String sample=args.length>5?args[5]:campaign+"_SAMPLE";
        var reservation=new GeminiSharedAllocation.Reservation(campaign,List.of(session),List.of(sample),1,INPUT_CAP,OUTPUT_CAP,INPUT_CAP+OUTPUT_CAP,new BigDecimal("0.02"));
        try(var allocation=new GeminiSharedAllocation(limits,reservation,Path.of(args[1]),Path.of(args[1]),GeminiExtractionUnblockTest.CLOCK)) {
            System.out.println("ACQUIRED"); System.out.flush();
            if(args[3].equals("crash")) Runtime.getRuntime().halt(0);
            if(args[3].equals("hold")) System.in.read();
            allocation.finish(true);
        } catch(ProviderException denied) { System.out.println("DENIED"); System.exit(10); }
    }
}
