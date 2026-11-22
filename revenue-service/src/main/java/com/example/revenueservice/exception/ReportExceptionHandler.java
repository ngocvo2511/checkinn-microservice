package com.example.revenueservice.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.Map;

@RestControllerAdvice
public class ReportExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ReportReconciliationRequiredException.class)
    public ResponseEntity<Object> reconciliation(ReportReconciliationRequiredException ex) {
        var body=envelope(503,"REPORT_RECONCILIATION_REQUIRED","Historical or conflicting cash transactions require reconciliation");
        return ResponseEntity.status(503).header("X-Request-ID",String.valueOf(body.get("requestId"))).body(body);
    }
    private ResponseEntity<Object> error(HttpStatusCode status) {
        String code=switch(status.value()) {case 400->"BAD_REQUEST";case 401->"UNAUTHORIZED";case 403->"FORBIDDEN";case 404->"NOT_FOUND";case 409->"CONFLICT";case 503->"DEPENDENCY_UNAVAILABLE";default->status.is5xxServerError()?"INTERNAL_ERROR":"REQUEST_FAILED";};
        var body=envelope(status.value(),code,code);
        return ResponseEntity.status(status).header("X-Request-ID",String.valueOf(body.get("requestId"))).body(body);
    }
    /** Standard envelope (STD-026, Batch 9H); existing status/code/message values are unchanged. */
    private static Map<String,Object> envelope(int status,String code,String message) {
        return com.example.revenueservice.api.ApiErrorSupport.body(status,code,message);
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex,Object body,HttpHeaders headers,HttpStatusCode status,WebRequest request) { return error(status); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Object> status(org.springframework.web.server.ResponseStatusException ex) { return error(ex.getStatusCode()); }
    @ExceptionHandler({IllegalArgumentException.class,jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Object> invalid(Exception ex) { return error(HttpStatus.BAD_REQUEST); }
    @ExceptionHandler({SecurityException.class,org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<Object> forbidden(Exception ex) { return error(HttpStatus.FORBIDDEN); }
    // CannotCreateTransactionException = no pool connection (not a DataAccessException); was 500 (STD-026, Batch 9F).
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.CannotCreateTransactionException.class,org.springframework.web.client.RestClientException.class})
    public ResponseEntity<Object> unavailable(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(ReportExceptionHandler.class).warn("[REVENUE_REPORT_DEPENDENCY_UNAVAILABLE]");
        return error(HttpStatus.SERVICE_UNAVAILABLE);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(ReportExceptionHandler.class).error("[REVENUE_REPORT_INTERNAL_ERROR]");
        return error(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
