package com.example.revenueservice.controller;

import com.example.revenueservice.dto.AdminRevenueResponse;
import com.example.revenueservice.dto.AdminSummaryResponse;
import com.example.revenueservice.dto.GroupBy;
import com.example.revenueservice.dto.OccupancyResponse;
import com.example.revenueservice.dto.RevenueResponse;
import com.example.revenueservice.dto.SummaryResponse;
import com.example.revenueservice.dto.OwnerRevenueResponse;
import com.example.revenueservice.dto.OwnerSummaryResponse;
import com.example.revenueservice.dto.TopHotelItem;
import com.example.revenueservice.service.RevenueReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class RevenueReportController {

    private final RevenueReportService revenueReportService;
    private final com.example.revenueservice.client.HotelGrpcClient hotels;

    private void requireAdmin(Jwt actor) {
        if (!"ADMIN".equals(actor.getClaimAsString("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin required");
    }
    private void requireOwner(String ownerId, Jwt actor) {
        if ("ADMIN".equals(actor.getClaimAsString("role"))) return;
        if (!"OWNER".equals(actor.getClaimAsString("role")) || !actor.getSubject().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Owner scope mismatch");
        }
    }
    private void requireHotel(String hotelId, Jwt actor) {
        if ("ADMIN".equals(actor.getClaimAsString("role"))) return;
        if (!"OWNER".equals(actor.getClaimAsString("role")) || hotelId == null || hotelId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hotel owner scope required");
        }
        var info = hotels.getHotelInfo(hotelId);
        if (info == null || info.ownerId() == null || info.ownerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cannot verify hotel ownership");
        }
        if (!actor.getSubject().equals(info.ownerId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hotel owner scope mismatch");
    }

    @GetMapping("/revenue")
    public RevenueResponse revenue(
            @RequestParam(required = false) String hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireHotel(hotelId, actor);
        validateDates(from, to);
        return revenueReportService.revenue(hotelId, from, to, GroupBy.fromString(groupBy));
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(required = false) String hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireHotel(hotelId, actor);
        validateDates(from, to);
        return revenueReportService.summary(hotelId, from, to);
    }

    @GetMapping("/occupancy")
    public OccupancyResponse occupancy(
            @RequestParam String hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireHotel(hotelId, actor);
        validateDates(from, to);
        return revenueReportService.occupancy(hotelId, from, to);
    }

    @GetMapping("/owner/revenue")
    public OwnerRevenueResponse ownerRevenue(
            @RequestParam String ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireOwner(ownerId, actor);
        validateDates(from, to);
        return revenueReportService.ownerRevenue(ownerId, from, to, GroupBy.fromString(groupBy));
    }

    @GetMapping("/owner/summary")
    public OwnerSummaryResponse ownerSummary(
            @RequestParam String ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireOwner(ownerId, actor);
        validateDates(from, to);
        return revenueReportService.ownerSummary(ownerId, from, to);
    }

    // ============== ADMIN ENDPOINTS ==============

    @GetMapping("/admin/summary")
    public AdminSummaryResponse adminSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireAdmin(actor);
        validateDates(from, to);
        return revenueReportService.adminSummary(from, to);
    }

    @GetMapping("/admin/revenue")
    public AdminRevenueResponse adminRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireAdmin(actor);
        validateDates(from, to);
        return revenueReportService.adminRevenue(from, to, GroupBy.fromString(groupBy));
    }

    @GetMapping("/admin/top-hotels")
    public List<TopHotelItem> adminTopHotels(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal Jwt actor
    ) {
        requireAdmin(actor);
        validateDates(from, to);
        return revenueReportService.getTopHotels(from, to, limit);
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Invalid date range");
        }
    }
}
