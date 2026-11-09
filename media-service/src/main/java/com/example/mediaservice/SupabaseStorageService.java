package com.example.mediaservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class SupabaseStorageService {
    private final HttpClient httpClient;
    private final ObjectMapper json=new ObjectMapper();
    private final String storageBaseUrl,bucket,privateBucket,key;
    private final long timeoutNanos;

    public SupabaseStorageService(String url,String bucket,String privateBucket,String key) {
        this(url,bucket,privateBucket,key,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),5000);
    }
    @Autowired
    public SupabaseStorageService(@Value("${supabase.url}") String url,@Value("${supabase.bucket}") String bucket,
            @Value("${supabase.private-bucket:hotel-documents}") String privateBucket,
            @Value("${supabase.service-role-key}") String key,HttpClient client,
            @Value("${supabase.request-timeout-ms:5000}") long timeoutMillis) {
        if(timeoutMillis<=0)throw new IllegalArgumentException("Positive storage request timeout required");
        this.storageBaseUrl=url.replaceAll("/+$","");this.bucket=bucket;this.privateBucket=privateBucket;
        this.key=key;this.httpClient=client;this.timeoutNanos=Duration.ofMillis(timeoutMillis).toNanos();
    }
    public String uploadMedia(byte[] bytes,String path,String mimeType) throws IOException,InterruptedException {
        String encoded=encodePath(path);
        upload(bytes,"/object/"+bucket+"/"+encoded,mimeType);
        return storageBaseUrl+"/storage/v1/object/public/"+bucket+"/"+encoded;
    }
    public String uploadPrivateDocument(byte[] bytes,String path,String mimeType) throws IOException,InterruptedException {
        upload(bytes,"/object/"+privateBucket+"/"+encodePath(path),mimeType);return path;
    }
    private void upload(byte[] bytes,String path,String mimeType) throws IOException,InterruptedException {
        if(bytes==null || bytes.length==0 || mimeType==null || mimeType.isBlank())throw new IllegalArgumentException("Valid file data and MIME type required");
        var response=send(request(path).header("Content-Type",mimeType).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)),deadline());
        requireSuccess(response);
    }
    public boolean deleteMedia(String path) throws IOException,InterruptedException {
        var response=send(request("/object/"+bucket+"/"+encodePath(path)).DELETE(),deadline());
        if(response.statusCode()==404)return false;
        requireSuccess(response);return response.statusCode()==200 || response.statusCode()==204;
    }
    public String createSignedUrl(String path,int expiry) throws IOException,InterruptedException {
        return signOne(path,expiry,deadline());
    }
    private String signOne(String path,int expiry,long deadline) throws IOException,InterruptedException {
        var response=send(request("/object/sign/"+privateBucket+"/"+encodePath(path)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("expiresIn",expiry>0?expiry:300)))),deadline);
        requireSuccess(response);return signedUrl(parse(response.body()),path);
    }
    public Map<String,String> createSignedUrls(List<String> paths,int expiry) throws IOException,InterruptedException {
        if(paths==null || paths.isEmpty())return Collections.emptyMap();
        var unique=new LinkedHashSet<String>();
        for(String path:paths){encodePath(path);unique.add(path);}
        long deadline=deadline();
        var response=send(request("/object/sign/"+privateBucket).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("paths",unique,"expiresIn",expiry>0?expiry:300)))),deadline);
        var result=new LinkedHashMap<String,String>();
        // Legacy servers without the batch route retain compatibility under ONE total time budget.
        if(response.statusCode()==404 || response.statusCode()==405) {
            for(String path:unique)result.put(path,signOne(path,expiry,deadline));return result;
        }
        requireSuccess(response);JsonNode root=parse(response.body());
        if(!root.isArray())throw protocol();
        for(JsonNode item:root) {
            JsonNode path=item.get("path"),error=item.get("error");
            if(path==null || !path.isTextual() || !unique.contains(path.asText()) || result.containsKey(path.asText())
                    || (error!=null && !error.isNull() && (!error.isTextual() || !error.asText().isBlank())))throw protocol();
            result.put(path.asText(),signedUrl(item,path.asText()));
        }
        if(result.size()!=unique.size())throw protocol();return result;
    }
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(storageBaseUrl+"/storage/v1"+path))
                .header("Authorization","Bearer "+key).header("apikey",key);
    }
    private long deadline(){return System.nanoTime()+timeoutNanos;}
    private HttpResponse<String> send(HttpRequest.Builder request,long deadline) throws IOException,InterruptedException {
        long remaining=deadline-System.nanoTime();
        if(remaining<=0)throw new HttpTimeoutException("Storage operation timed out");
        var pending=httpClient.sendAsync(request.timeout(Duration.ofNanos(remaining)).build(),HttpResponse.BodyHandlers.ofString());
        try { return pending.get(remaining,java.util.concurrent.TimeUnit.NANOSECONDS); }
        catch(java.util.concurrent.TimeoutException timeout) {
            pending.cancel(true);throw new HttpTimeoutException("Storage operation timed out");
        } catch(InterruptedException interrupted) { pending.cancel(true);throw interrupted;
        } catch(java.util.concurrent.ExecutionException failure) {
            if(failure.getCause() instanceof HttpTimeoutException)throw new HttpTimeoutException("Storage operation timed out");
            throw new IOException("Storage transport unavailable");
        }
    }
    private void requireSuccess(HttpResponse<?> response) throws IOException {
        if(response.statusCode()<200 || response.statusCode()>=300)throw new IOException("Storage request failed (HTTP "+response.statusCode()+")");
    }
    private JsonNode parse(String body) throws IOException {
        try {JsonNode value=json.readTree(body);if(value==null)throw protocol();return value;}
        catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw protocol();}
    }
    private String signedUrl(JsonNode value,String expectedPath) throws IOException {
        JsonNode url=value.get("signedURL");if(url==null || url.isNull())url=value.get("signedUrl");
        if(url==null || !url.isTextual() || url.asText().isBlank())throw protocol();
        String part=url.asText(),full;
        if(part.startsWith("http://") || part.startsWith("https://"))full=part;
        else if(part.startsWith("/storage/v1/"))full=storageBaseUrl+part;
        else full=storageBaseUrl+"/storage/v1/"+part.replaceFirst("^/","");
        try {
            URI result=URI.create(full),base=URI.create(storageBaseUrl);
            if(!Objects.equals(result.getScheme(),base.getScheme()) || !Objects.equals(result.getRawAuthority(),base.getRawAuthority())
                    || result.getUserInfo()!=null || result.getFragment()!=null
                    || !result.getPath().equals(base.getPath()+"/storage/v1/object/sign/"+privateBucket+"/"+expectedPath))throw protocol();
            return result.toASCIIString();
        }catch(IllegalArgumentException malformed){throw protocol();}
    }
    private IOException protocol(){return new IOException("Storage returned an invalid or incomplete signing response");}
    private String encodePath(String path) {
        if(path==null || path.isBlank() || path.startsWith("/") || path.endsWith("/") || path.contains("\\")
                || path.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid storage object path");
        var encoded=new ArrayList<String>();
        for(String segment:path.split("/",-1)) {
            if(segment.isEmpty() || segment.equals(".") || segment.equals(".."))throw new IllegalArgumentException("Invalid storage object path");
            encoded.add(URLEncoder.encode(segment,StandardCharsets.UTF_8).replace("+","%20"));
        }
        return String.join("/",encoded);
    }
}
