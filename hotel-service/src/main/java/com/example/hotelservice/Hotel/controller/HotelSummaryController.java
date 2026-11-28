package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.dto.response.HotelSummaryResponse;
import com.example.hotelservice.Hotel.service.HotelSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class HotelSummaryController {
    private final HotelSummaryService summaries;

    @GetMapping("/batch")
    public List<HotelSummaryResponse> summarize(@RequestParam List<UUID> ids) {
        return summaries.summarize(ids);
    }
}
