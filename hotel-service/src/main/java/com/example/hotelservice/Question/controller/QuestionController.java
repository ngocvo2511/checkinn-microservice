package com.example.hotelservice.Question.controller;

import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Question.dto.QuestionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
@Slf4j
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
        log.info("[QUESTION_CONTROLLER_UPDATE] Update questions request received - hotelId: {}, ownerId: {}, questionsCount: {}",
            hotelId, ownerId, questions != null ? questions.size() : 0);
        var updated = hotelService.updateQuestions(hotelId, questions, ownerId);
        log.info("[QUESTION_CONTROLLER_UPDATE] Update questions success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @PostMapping("/{hotelId}/questions")
    public ResponseEntity<HotelResponse> addQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody QuestionRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[QUESTION_CONTROLLER_ADD] Add question request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
        var updated = hotelService.addQuestion(hotelId, request, ownerId);
        log.info("[QUESTION_CONTROLLER_ADD] Add question success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @DeleteMapping("/{hotelId}/questions")
    public ResponseEntity<HotelResponse> clearQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[QUESTION_CONTROLLER_CLEAR] Clear questions request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
        var updated = hotelService.clearQuestions(hotelId, ownerId);
        log.info("[QUESTION_CONTROLLER_CLEAR] Clear questions success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    @DeleteMapping("/{hotelId}/questions/{questionId}")
    public ResponseEntity<HotelResponse> removeQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @PathVariable UUID questionId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[QUESTION_CONTROLLER_REMOVE] Remove question request received - hotelId: {}, ownerId: {}, questionId: {}",
            hotelId, ownerId, questionId);
        var updated = hotelService.removeQuestion(hotelId, questionId, ownerId);
        log.info("[QUESTION_CONTROLLER_REMOVE] Remove question success - hotelId: {}, ownerId: {}, questionId: {}",
            hotelId, ownerId, questionId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }
}
