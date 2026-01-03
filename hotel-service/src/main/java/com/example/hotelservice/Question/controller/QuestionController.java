package com.example.hotelservice.Question.controller;

import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Question.dto.QuestionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class QuestionController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    private UUID getOwnerId(String header) {
        return UUID.fromString(header);
    }

    @PutMapping("/{hotelId}/questions")
    public ResponseEntity<HotelResponse> updateQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody List<QuestionRequest> questions
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        var updated = hotelService.updateQuestions(hotelId, questions, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @PostMapping("/{hotelId}/questions")
    public ResponseEntity<HotelResponse> addQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody QuestionRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        var updated = hotelService.addQuestion(hotelId, request, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @DeleteMapping("/{hotelId}/questions")
    public ResponseEntity<HotelResponse> clearQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        var updated = hotelService.clearQuestions(hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @DeleteMapping("/{hotelId}/questions/{questionId}")
    public ResponseEntity<HotelResponse> removeQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @PathVariable UUID questionId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        var updated = hotelService.removeQuestion(hotelId, questionId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }
}
