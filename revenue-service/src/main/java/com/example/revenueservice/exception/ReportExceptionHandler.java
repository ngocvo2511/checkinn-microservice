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
        return ResponseEntity.status(503).body(Map.of("status",503,"code","REPORT_RECONCILIATION_REQUIRED","message","Historical or conflicting cash transactions require reconciliation"));
    }
    private ResponseEntity<Object> error(HttpStatusCode status) {
        String code=switch(status.value()) {case 400->"BAD_REQUEST";case 401->"UNAUTHORIZED";case 403->"FORBIDDEN";case 404->"NOT_FOUND";case 409->"CONFLICT";case 503->"DEPENDENCY_UNAVAILABLE";default->status.is5xxServerError()?"INTERNAL_ERROR":"REQUEST_FAILED";};
        return ResponseEntity.status(status).body(Map.of("status",status.value(),"code",code,"message",code));
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex,Object body,HttpHeaders headers,HttpStatusCode status,WebRequest request) { return error(status); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Object> status(org.springframework.web.server.ResponseStatusException ex) { return error(ex.getStatusCode()); }
    @ExceptionHandler({IllegalArgumentException.class,jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Object> invalid(Exception ex) { return error(HttpStatus.BAD_REQUEST); }
    @ExceptionHandler({SecurityException.class,org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<Object> forbidden(Exception ex) { return error(HttpStatus.FORBIDDEN); }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.web.client.RestClientException.class})
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
