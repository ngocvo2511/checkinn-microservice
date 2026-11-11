package com.example.regulationsservice.api;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class RegulationErrorAdvice {
    private ResponseEntity<?> error(int status,String code,String message) {
        return ResponseEntity.status(status).body(Map.of("status",status,"code",code,"message",message));
    }
    @ExceptionHandler({OptimisticLockingFailureException.class,DataIntegrityViolationException.class})
    ResponseEntity<?> conflict(Exception failure) { return error(409,"REGULATION_CONFLICT","Regulation has changed; reload before saving"); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(Exception failure) { return error(503,"DEPENDENCY_UNAVAILABLE","Regulation storage unavailable"); }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception failure) { return error(400,"INVALID_REQUEST","Invalid regulation request"); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException failure) {
        int status=failure.getStatusCode().value();
        return error(status,status==409?"REGULATION_CONFLICT":status==400?"INVALID_REQUEST":"REQUEST_REJECTED",
                status==409?"Regulation has changed; reload before saving":"Regulation request rejected");
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> invalidStoredValue(Exception failure) { return error(503,"REGULATION_UNAVAILABLE","Required regulation unavailable"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception failure) {
        if(failure instanceof org.springframework.web.ErrorResponse known) {
            int status=known.getStatusCode().value();
            String code=switch(status) {case 404->"NOT_FOUND";case 405->"METHOD_NOT_ALLOWED";
                case 406->"NOT_ACCEPTABLE";case 415->"UNSUPPORTED_MEDIA_TYPE";default->"REQUEST_REJECTED";};
            var response=error(status,code,"Regulation request rejected");
            if(failure instanceof org.springframework.web.HttpRequestMethodNotSupportedException method
                    && method.getSupportedHttpMethods()!=null) {
                var headers=new HttpHeaders();headers.setAllow(method.getSupportedHttpMethods());
                return new ResponseEntity<>(response.getBody(),headers,response.getStatusCode());
            }
            return response;
        }
        return error(500,"INTERNAL_ERROR","Unable to complete regulation request");
    }
}
