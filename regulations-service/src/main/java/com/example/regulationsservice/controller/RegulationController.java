package com.example.regulationsservice.controller;

import com.example.regulationsservice.dto.CommissionRateDto;
import com.example.regulationsservice.dto.PointsConversionDto;
import com.example.regulationsservice.dto.RegulationDto;
import com.example.regulationsservice.dto.RegulationSnapshotDto;
import com.example.regulationsservice.service.RegulationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RegulationController {

    private final RegulationService regulationService;

    public RegulationController(RegulationService regulationService) {
        this.regulationService = regulationService;
    }

    @GetMapping
    public ResponseEntity<List<RegulationDto>> getRegulations() {
        log.info("[REGULATION_CONTROLLER_LIST] Fetch regulations request received");
        List<RegulationDto> regulations = regulationService.listRegulations();
        log.info("[REGULATION_CONTROLLER_LIST] Fetch regulations success - count: {}", regulations.size());
        return ResponseEntity.ok(regulations);
    }

    @GetMapping("/{key}")
    public ResponseEntity<RegulationDto> getRegulation(@PathVariable("key") String key) {
        log.info("[REGULATION_CONTROLLER_GET] Fetch regulation request received - key: {}", key);
        return regulationService.findRegulation(key)
                .map(regulation -> {
                    log.info("[REGULATION_CONTROLLER_GET] Fetch regulation success - key: {}", key);
                    return ResponseEntity.ok(regulation);
                })
                .orElseGet(() -> {
                    log.info("[REGULATION_CONTROLLER_GET] Regulation not found - key: {}", key);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/typed/commission")
    public ResponseEntity<CommissionRateDto> getTypedCommissionRate() {
        log.info("[REGULATION_CONTROLLER_GET_COMMISSION] Fetch commission rate request received");
        CommissionRateDto dto = regulationService.getCommissionRateDto();
        log.info("[REGULATION_CONTROLLER_GET_COMMISSION] Fetch commission rate success - rate: {}", dto.getCommissionRate());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/typed/points")
    public ResponseEntity<PointsConversionDto> getTypedPointsConversionRates() {
        log.info("[REGULATION_CONTROLLER_GET_POINTS] Fetch points conversion rates request received");
        PointsConversionDto dto = regulationService.getPointsConversionRatesDto();
        log.info("[REGULATION_CONTROLLER_GET_POINTS] Fetch points conversion rates success - earnRate: {}, redemptionRate: {}",
                dto.getEarnConversionRate(), dto.getRedemptionConversionRate());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<RegulationSnapshotDto>> getSnapshots() {
        log.info("[REGULATION_CONTROLLER_SNAPSHOTS] Fetch snapshots request received");
        List<RegulationSnapshotDto> snapshots = regulationService.listSnapshots();
        log.info("[REGULATION_CONTROLLER_SNAPSHOTS] Fetch snapshots success - count: {}", snapshots.size());
        return ResponseEntity.ok(snapshots);
    }

    @PutMapping("/{key}")
    public ResponseEntity<RegulationDto> updateRegulation(
            @PathVariable("key") String key,
            @Valid @RequestBody RegulationDto request,
            Authentication authentication) {
        verifyAdminRole(authentication);
        String changedBy = authentication != null ? authentication.getName() : "anonymous";
        log.info("[REGULATION_CONTROLLER_UPDATE] Update regulation request received - key: {}, changedBy: {}, version: {}",
            key, changedBy, request.getVersion());
        RegulationDto saved = regulationService.upsertRegulation(key, request, changedBy);
        log.info("[REGULATION_CONTROLLER_UPDATE] Update regulation success - key: {}, changedBy: {}, version: {}",
            key, changedBy, saved.getVersion());
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
        log.info("[REGULATION_CONTROLLER_REFRESH] Refresh cache request received");
        regulationService.refreshCache();
        log.info("[REGULATION_CONTROLLER_REFRESH] Refresh cache success");
        return ResponseEntity.ok().build();
    }
}
