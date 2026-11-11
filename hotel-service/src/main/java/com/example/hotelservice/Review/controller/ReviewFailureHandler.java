package com.example.hotelservice.Review.controller;

import com.example.hotelservice.Review.service.ReviewConflictException;
import com.example.hotelservice.api.ApiErrorSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(-1)
@RestControllerAdvice(assignableTypes = ReviewController.class)
public class ReviewFailureHandler {
    @ExceptionHandler(ReviewConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiErrorSupport.body(409, request.getRequestURI(),
                Map.of("message", "Review or response already exists")));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> unavailable(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrorSupport.body(503, request.getRequestURI(),
                Map.of("message", "Review storage temporarily unavailable")));
    }
}
