package com.example.regulationsservice.service;

import com.example.regulationsservice.dto.CommissionRateDto;
import com.example.regulationsservice.dto.PointsConversionDto;
import com.example.regulationsservice.dto.RegulationDto;
import com.example.regulationsservice.dto.RegulationSnapshotDto;
import com.example.regulationsservice.model.Regulation;
import com.example.regulationsservice.model.RegulationSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RegulationService {

    private static final String COMMISSION_RATE_KEY = "COMMISSION_RATE";
    private static final String POINTS_EARN_CONVERSION_RATE_KEY = "POINTS_EARN_CONVERSION_RATE";
    private static final String POINTS_REDEMPTION_CONVERSION_RATE_KEY = "POINTS_REDEMPTION_CONVERSION_RATE";

    private final RegulationProvider regulationProvider;

    public RegulationService(RegulationProvider regulationProvider) {
        this.regulationProvider = regulationProvider;
    }

    public List<RegulationDto> listRegulations() {
        return regulationProvider.getAllRegulations().stream()
                .map(RegulationService::toDto)
                .collect(Collectors.toList());
    }

    public Optional<RegulationDto> findRegulation(String regulationKey) {
        return regulationProvider.getRegulation(regulationKey).map(RegulationService::toDto);
    }

    public RegulationDto upsertRegulation(String regulationKey, RegulationDto request, String changedBy) {
        Regulation regulation = new Regulation(
                regulationKey,
                request.getName(),
                request.getValue(),
                request.getDescription(),
                request.isActive(),
                request.getVersion()
        );

        Regulation saved = regulationProvider.saveRegulation(regulation, changedBy);
        return toDto(saved);
    }

    public List<RegulationSnapshotDto> listSnapshots() {
        return regulationProvider.getSnapshots().stream()
                .map(RegulationService::toSnapshotDto)
                .collect(Collectors.toList());
    }

    public CommissionRateDto getCommissionRateDto() {
        BigDecimal commissionRate = getRegulationValueAsDecimal(COMMISSION_RATE_KEY);
        return new CommissionRateDto(commissionRate);
    }

    public PointsConversionDto getPointsConversionRatesDto() {
        BigDecimal earnRate = getRegulationValueAsDecimal(POINTS_EARN_CONVERSION_RATE_KEY);
        BigDecimal redemptionRate = getRegulationValueAsDecimal(POINTS_REDEMPTION_CONVERSION_RATE_KEY);
        return new PointsConversionDto(earnRate, redemptionRate);
    }

    public void refreshCache() {
        regulationProvider.refreshCache();
    }

    private BigDecimal getRegulationValueAsDecimal(String regulationKey) {
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
