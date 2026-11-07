package com.example.hotelservice.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(com.example.hotelservice.Room.availability.service.InventoryInconsistentException.class)
    public ResponseEntity<Map<String, Object>> handleInventoryInconsistent(
            com.example.hotelservice.Room.availability.service.InventoryInconsistentException ex,
            HttpServletRequest req) {
        var response=buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
        response.getBody().put("code","INVENTORY_INCONSISTENT");
        return response;
    }

    @ExceptionHandler(com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleInventoryNotConfigured(
            com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException ex,
            HttpServletRequest req) {
        var response=buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
        response.getBody().put("code","INVENTORY_NOT_CONFIGURED");
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("[GLOBAL_EXCEPTION] IllegalArgumentException - path: {}, message: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.warn("[GLOBAL_EXCEPTION] IllegalStateException - path: {}, message: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDenied(RuntimeException ex, HttpServletRequest req) {
        log.warn("[GLOBAL_EXCEPTION] AccessDeniedException - path: {}, message: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        log.warn("[GLOBAL_EXCEPTION] EntityNotFoundException - path: {}, message: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String,String> fields=new java.util.TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.merge(e.getField(),e.getDefaultMessage(),(a,b)->a.compareTo(b)<=0?a:b));
        return validationResponse(fields,req);
    }

    @ExceptionHandler(com.example.hotelservice.validation.InputValidation.InvalidInput.class)
    public ResponseEntity<Map<String,Object>> invalidInput(com.example.hotelservice.validation.InputValidation.InvalidInput ex, HttpServletRequest req) {
        return validationResponse(ex.getFieldErrors(),req);
    }

    private ResponseEntity<Map<String,Object>> validationResponse(Map<String,String> fields,HttpServletRequest req) {
        var result=buildErrorResponse(HttpStatus.BAD_REQUEST,"Invalid request fields",req.getRequestURI());
        result.getBody().put("fieldErrors",fields);
        return result;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.warn("[GLOBAL_EXCEPTION] ResponseStatusException - path: {}, message: {}", req.getRequestURI(), ex.getReason());
        return buildErrorResponse(status, ex.getReason(), req.getRequestURI());
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.validation.BindException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String,Object>> malformed(Exception ex, HttpServletRequest req) {
        return validationResponse(Map.of("request","Malformed request"),req);
    }

    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<Map<String,Object>> stale(Exception ex, HttpServletRequest req) {
        return buildErrorResponse(HttpStatus.CONFLICT,"Thông tin đã thay đổi. Hãy tải lại.",req.getRequestURI());
    }


    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<Map<String,Object>> framework(Exception ex,HttpServletRequest req) {
        var error=(org.springframework.web.ErrorResponse)ex;
        var status=HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).headers(error.getHeaders()).body(buildErrorResponse(status,status.getReasonPhrase(),req.getRequestURI()).getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest req) {
        var response=buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,"Internal server error",req.getRequestURI());
        log.error("Unhandled API exception - type: {}, path: {}, requestId: {}",ex.getClass().getSimpleName(),req.getRequestURI(),response.getBody().get("requestId"));
        return response;
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(com.example.hotelservice.api.ApiErrorSupport.body(status.value(),path,Map.of("message",message==null?status.getReasonPhrase():message)));
    }
}
