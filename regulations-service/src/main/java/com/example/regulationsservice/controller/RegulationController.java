package com.example.regulationsservice.controller;

import com.example.regulationsservice.dto.CommissionRateDto;
import com.example.regulationsservice.dto.PointsConversionDto;
import com.example.regulationsservice.dto.RegulationDto;
import com.example.regulationsservice.dto.RegulationSnapshotDto;
import com.example.regulationsservice.service.RegulationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/regulations")
public class RegulationController {

    private final RegulationService regulationService;

    public RegulationController(RegulationService regulationService) {
        this.regulationService = regulationService;
    }

    @GetMapping
    public ResponseEntity<List<RegulationDto>> getRegulations() {
        return ResponseEntity.ok(regulationService.listRegulations());
    }

    @GetMapping("/{key}")
    public ResponseEntity<RegulationDto> getRegulation(@PathVariable("key") String key) {
        return regulationService.findRegulation(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/typed/commission")
    public ResponseEntity<CommissionRateDto> getTypedCommissionRate() {
        return ResponseEntity.ok(regulationService.getCommissionRateDto());
    }

    @GetMapping("/typed/points")
    public ResponseEntity<PointsConversionDto> getTypedPointsConversionRates() {
        return ResponseEntity.ok(regulationService.getPointsConversionRatesDto());
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<RegulationSnapshotDto>> getSnapshots() {
        return ResponseEntity.ok(regulationService.listSnapshots());
    }

    @PutMapping("/{key}")
    public ResponseEntity<RegulationDto> updateRegulation(
            @PathVariable("key") String key,
            @Valid @RequestBody RegulationDto request,
            Authentication authentication) {
        verifyAdminRole(authentication);
        String changedBy = authentication != null ? authentication.getName() : "anonymous";
        RegulationDto saved = regulationService.upsertRegulation(key, request, changedBy);
        return ResponseEntity.ok(saved);
    }

    private void verifyAdminRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(it -> "ROLE_ADMIN".equals(it.getAuthority()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only ADMIN users can update regulations"
            );
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshCache() {
        regulationService.refreshCache();
        return ResponseEntity.ok().build();
    }
}
