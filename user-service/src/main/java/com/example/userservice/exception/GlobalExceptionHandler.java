package com.example.userservice.exception;

import com.example.userservice.api.ApiErrorSupport;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * user-service errors use the standard envelope {timestamp,status,error,code,message,path,requestId} + X-Request-ID
 * (STD-026, Batch 9H). Statuses and client messages are unchanged from Batch 9G; only deliberate business
 * messages (UserBusinessException) and fixed texts leave the service (F-9F-1).
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedJson(Exception error) {
        return ApiErrorSupport.response(400, "INVALID_REQUEST", "Malformed request or UUID");
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception ex) {
        return ApiErrorSupport.response(404, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<Map<String, Object>> invalidPointsInput(RuntimeException ex) {
        return ApiErrorSupport.response(400, "INVALID_REQUEST", "Invalid request or insufficient points");
    }

    // CannotCreateTransactionException (no pool connection) is not a DataAccessException; without it a DB outage
    // fell through to the RuntimeException handler as 401 with the raw exception text (STD-026, Batch 9F).
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, Object>> pointsStorageUnavailable(Exception ex) {
        return ApiErrorSupport.response(503, "DEPENDENCY_UNAVAILABLE", "Points storage unavailable; retry the same command");
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        return ApiErrorSupport.response(status, null, status >= 500 ? null : (ex.getReason() == null ? "Request rejected" : ex.getReason()));
    }

    /** Deliberate business rejection: unchanged contract (401 + the user-facing message). */
    @ExceptionHandler(UserBusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRejection(UserBusinessException ex) {
        return ApiErrorSupport.response(401, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(Exception ex) {
        return ApiErrorSupport.response(403, "FORBIDDEN", "Access denied");
    }

    /** Redis session store outage fails closed as a dependency failure, never as success (AGENTS). */
    @ExceptionHandler(com.example.userservice.security.SessionStoreUnavailableException.class)
    public ResponseEntity<Map<String, Object>> sessionStoreUnavailable(Exception ex) {
        return ApiErrorSupport.response(503, "DEPENDENCY_UNAVAILABLE", "Session verification unavailable");
    }

    /** Request-shape errors detected by Spring keep their 4xx status, with a generic message. */
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.validation.BindException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<Map<String, Object>> requestShape(Exception ex) {
        int status = ex instanceof org.springframework.web.ErrorResponse known ? known.getStatusCode().value() : 400;
        return ApiErrorSupport.response(status, status == 400 ? "INVALID_REQUEST" : null, "Invalid request");
    }

    /** Anything else is unexpected: a generic 500 without the exception text (F-9F-1). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("[USER_UNHANDLED_EXCEPTION] type: {}", ex.getClass().getSimpleName());
        return ApiErrorSupport.response(500, "INTERNAL_ERROR", "Internal server error");
    }
}
