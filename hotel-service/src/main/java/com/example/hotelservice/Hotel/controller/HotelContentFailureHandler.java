package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Policy.controller.PolicyController;
import com.example.hotelservice.Question.controller.QuestionController;
import com.example.hotelservice.api.ApiErrorSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(-1)
@RestControllerAdvice(assignableTypes={com.example.hotelservice.MediaAsset.controller.MediaAssetController.class,PolicyController.class,QuestionController.class,HotelController.class,com.example.hotelservice.Amenity.controller.AmenityController.class})
public class HotelContentFailureHandler {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,Object>> unavailable(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrorSupport.body(503,request.getRequestURI(),
                Map.of("message","Hotel storage temporarily unavailable")));
    }
}
