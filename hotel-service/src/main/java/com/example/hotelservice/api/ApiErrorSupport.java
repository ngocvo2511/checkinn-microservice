package com.example.hotelservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Additive HTTP error contract. Never publishes an unexpected exception body. */
public final class ApiErrorSupport {
    private ApiErrorSupport() {}
    private static final ObjectMapper JSON = new ObjectMapper();
    public static Map<String,Object> body(int status,String path,Object original) {
        HttpStatus known=HttpStatus.resolve(status);
        String reason=known==null?"Request failed":known.getReasonPhrase();
        Map<?,?> source=original instanceof Map<?,?> map?map:Map.of();
        String code=switch(status) {
            case 400 -> "INVALID_REQUEST"; case 401 -> "UNAUTHORIZED"; case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND"; case 405 -> "METHOD_NOT_ALLOWED"; case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE"; case 429 -> "RATE_LIMITED";
            case 502,503 -> "DEPENDENCY_UNAVAILABLE"; case 504 -> "DEPENDENCY_TIMEOUT";
            default -> status>=500?"INTERNAL_ERROR":"REQUEST_FAILED";
        };
        String message=status>=500 ? (status==503||status==502?"Service temporarily unavailable":status==504?"Dependency timed out":"Internal server error") : reason;
        if(status<500) {
            if(source.get("message") instanceof String text && !text.isBlank()) message=text;
            else if(original instanceof String text && !text.isBlank()) message=text;
            if(source.get("code") instanceof String text && text.matches("[A-Z][A-Z0-9_]{0,63}")) code=text;
            else if(source.get("fieldErrors") instanceof Map<?,?>) code="VALIDATION_ERROR";
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("timestamp",Instant.now().toString()); result.put("status",status); result.put("error",reason);
        result.put("code",code); result.put("message",message); result.put("path",path);
        String trace=source.get("requestId") instanceof String id?id:org.slf4j.MDC.get("traceId");
        result.put("requestId",trace!=null&&trace.matches("[A-Za-z0-9_-]{1,64}")?trace:UUID.randomUUID().toString());
        if(status<500 && source.get("fieldErrors") instanceof Map<?,?> fields) {
            Map<String,String> safeFields=new TreeMap<>();
            fields.forEach((key,value)->{if(key instanceof String k && value instanceof String v) safeFields.put(k,v);});
            result.put("fieldErrors",safeFields);
        }
        return result;
    }
    public static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize API error"); }
    }
    public static void write(HttpServletResponse response,int status,String path) throws java.io.IOException {
        var body=body(status,path,null);
        response.setStatus(status);response.setCharacterEncoding("UTF-8");response.setContentType("application/json");
        response.setHeader("X-Request-ID",body.get("requestId").toString());response.setHeader("Cache-Control","no-store");
        response.getWriter().write(json(body));
    }
}
