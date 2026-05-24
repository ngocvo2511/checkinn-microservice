package com.example.regulationsservice.service;

import com.example.regulationsservice.dto.CommissionRateDto;
import com.example.regulationsservice.dto.PointsConversionDto;
import com.example.regulationsservice.dto.RegulationDto;
import com.example.regulationsservice.dto.RegulationSnapshotDto;
import com.example.regulationsservice.model.Regulation;
import com.example.regulationsservice.model.RegulationSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RegulationService {

    private static final String COMMISSION_RATE_KEY = "COMMISSION_RATE";
    private static final String POINTS_EARN_CONVERSION_RATE_KEY = "POINTS_EARN_CONVERSION_RATE";
    private static final String POINTS_REDEMPTION_CONVERSION_RATE_KEY = "POINTS_REDEMPTION_CONVERSION_RATE";

    private final RegulationProvider regulationProvider;

    public RegulationService(RegulationProvider regulationProvider) {
        this.regulationProvider = regulationProvider;
    }

    public List<RegulationDto> listRegulations() {
        List<RegulationDto> regulations = regulationProvider.getAllRegulations().stream()
                .map(RegulationService::toDto)
                .collect(Collectors.toList());
        log.info("[REGULATION_SERVICE_LIST] Loaded regulations from provider - count: {}", regulations.size());
        return regulations;
    }

    public Optional<RegulationDto> findRegulation(String regulationKey) {
        log.info("[REGULATION_SERVICE_GET] Looking up regulation - key: {}", regulationKey);
        return regulationProvider.getRegulation(regulationKey)
                .map(RegulationService::toDto)
                .map(dto -> {
                    log.info("[REGULATION_SERVICE_GET] Regulation found - key: {}", regulationKey);
                    return dto;
                });
    }

    public RegulationDto upsertRegulation(String regulationKey, RegulationDto request, String changedBy) {
        log.info("[REGULATION_SERVICE_UPSERT] Upserting regulation - key: {}, changedBy: {}, active: {}, version: {}",
                regulationKey, changedBy, request.isActive(), request.getVersion());
        Regulation regulation = new Regulation(
                regulationKey,
                request.getName(),
                request.getValue(),
                request.getDescription(),
                request.isActive(),
                request.getVersion()
        );

        Regulation saved = regulationProvider.saveRegulation(regulation, changedBy);
        log.info("[REGULATION_SERVICE_UPSERT] Regulation upserted - key: {}, version: {}", regulationKey, saved.getVersion());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RegulationSnapshotDto> listSnapshots() {
        try {
            List<RegulationSnapshotDto> snapshots = regulationProvider.getSnapshots().stream()
                    .map(RegulationService::toSnapshotDto)
                    .collect(Collectors.toList());
            log.info("[REGULATION_SERVICE_SNAPSHOTS] Loaded snapshots - count: {}", snapshots.size());
            return snapshots;
        } catch (Exception ex) {
            log.warn("[REGULATION_SERVICE_SNAPSHOTS_ERROR] Unable to load regulation snapshots: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    public CommissionRateDto getCommissionRateDto() {
        BigDecimal commissionRate = getRegulationValueAsDecimal(COMMISSION_RATE_KEY);
        log.info("[REGULATION_SERVICE_TYPED_COMMISSION] Commission rate resolved - value: {}", commissionRate);
        return new CommissionRateDto(commissionRate);
    }

    public PointsConversionDto getPointsConversionRatesDto() {
        BigDecimal earnRate = getRegulationValueAsDecimal(POINTS_EARN_CONVERSION_RATE_KEY);
        BigDecimal redemptionRate = getRegulationValueAsDecimal(POINTS_REDEMPTION_CONVERSION_RATE_KEY);
        log.info("[REGULATION_SERVICE_TYPED_POINTS] Points conversion rates resolved - earnRate: {}, redemptionRate: {}",
                earnRate, redemptionRate);
        return new PointsConversionDto(earnRate, redemptionRate);
    }

    public void refreshCache() {
        log.info("[REGULATION_SERVICE_REFRESH] Refreshing regulation cache");
        regulationProvider.refreshCache();
        log.info("[REGULATION_SERVICE_REFRESH] Regulation cache refreshed");
    }

    private BigDecimal getRegulationValueAsDecimal(String regulationKey) {
        log.info("[REGULATION_SERVICE_PARSE] Resolving regulation value as decimal - key: {}", regulationKey);
        return regulationProvider.getRegulation(regulationKey)
                .filter(Regulation::isActive)
                .map(Regulation::getValue)
                .map(value -> parseDecimalValue(value, regulationKey))
                .orElseThrow(() -> new IllegalStateException("Active regulation not found for key: " + regulationKey));
    }

    private BigDecimal parseDecimalValue(String value, String regulationKey) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Regulation value for key '" + regulationKey + "' is not a valid decimal: " + value, ex);
        }
    }

    private static RegulationDto toDto(Regulation regulation) {
        RegulationDto dto = new RegulationDto();
        dto.setRegulationKey(regulation.getRegulationKey());
        dto.setName(regulation.getName());
        dto.setValue(regulation.getValue());
        dto.setDescription(regulation.getDescription());
        dto.setActive(regulation.isActive());
        dto.setVersion(regulation.getVersion());
        return dto;
    }

    private static RegulationSnapshotDto toSnapshotDto(RegulationSnapshot snapshot) {
        RegulationSnapshotDto dto = new RegulationSnapshotDto();
        dto.setRegulationKey(snapshot.getRegulationKey());
        dto.setRegulationName(snapshot.getRegulationName());
        dto.setVersion(snapshot.getVersion());
        dto.setSourceUser(snapshot.getSourceUser());
        dto.setAppliedAt(snapshot.getAppliedAt());
        dto.setSnapshotData(snapshot.getSnapshotData());
        return dto;
    }
}
