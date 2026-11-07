package com.example.bookingservice.validation;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex,HttpServletRequest req) {
        Map<String,String> fields=new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.merge(e.getField(),e.getDefaultMessage(),(a,b)->a.compareTo(b)<=0?a:b));
        return response(fields,req);
    }
    @ExceptionHandler(InputValidation.InvalidInput.class)
    public ResponseEntity<Map<String,Object>> validation(InputValidation.InvalidInput ex,HttpServletRequest req) { return response(ex.getFieldErrors(),req); }
    @ExceptionHandler({HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.validation.BindException.class})
    public ResponseEntity<Map<String,Object>> malformed(Exception ex,HttpServletRequest req) { return response(Map.of("request","Malformed request"),req); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> status(org.springframework.web.server.ResponseStatusException ex,HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(com.example.bookingservice.api.ApiErrorSupport.body(ex.getStatusCode().value(),req.getRequestURI(),Map.of("message",ex.getReason()==null?"Request failed":ex.getReason())));
    }
    @ExceptionHandler({org.springframework.security.access.AccessDeniedException.class,SecurityException.class})
    public ResponseEntity<Map<String,Object>> forbidden(Exception ex,HttpServletRequest req) { return error(403,req,null); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> invalid(Exception ex,HttpServletRequest req) { return error(400,req,ex.getMessage()); }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<Map<String,Object>> framework(Exception ex,HttpServletRequest req) {
        var error=(org.springframework.web.ErrorResponse)ex;
        return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(com.example.bookingservice.api.ApiErrorSupport.body(error.getStatusCode().value(),req.getRequestURI(),null));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception ex,HttpServletRequest req) {
        var response=error(500,req,null);
        org.slf4j.LoggerFactory.getLogger(ValidationAdvice.class).error("Unhandled API exception - type: {}, path: {}, requestId: {}",ex.getClass().getSimpleName(),req.getRequestURI(),response.getBody().get("requestId"));
        return response;
    }
    private ResponseEntity<Map<String,Object>> error(int status,HttpServletRequest req,String message) {
        return ResponseEntity.status(status).body(com.example.bookingservice.api.ApiErrorSupport.body(status,req.getRequestURI(),message==null?null:Map.of("message",message)));
    }
    private ResponseEntity<Map<String,Object>> response(Map<String,String> fields,HttpServletRequest req) {
        return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now().toString(),"status",400,"error","Bad Request",
                "message","Invalid request fields","path",req.getRequestURI(),"fieldErrors",fields));
    }
}
