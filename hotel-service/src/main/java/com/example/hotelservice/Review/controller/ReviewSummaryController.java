package com.example.hotelservice.Review.controller;

import com.example.hotelservice.Review.dto.response.ReviewSummaryResponse;
import com.example.hotelservice.Review.service.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewSummaryController {
    private final ReviewSummaryService summaries;

    @GetMapping("/summaries")
    public List<ReviewSummaryResponse> summarize(@RequestParam List<UUID> hotelIds) {
        return summaries.summarize(hotelIds);
    }
}
