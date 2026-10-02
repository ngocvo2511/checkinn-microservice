package com.example.regulationsservice.service;

import com.example.regulationsservice.dto.CommissionRateDto;
import com.example.regulationsservice.dto.PointsConversionDto;
import com.example.regulationsservice.model.Regulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RegulationServiceTest {

    @Mock
    private RegulationProvider regulationProvider;

    private RegulationService regulationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        regulationService = new RegulationService(regulationProvider);
    }

    @Test
    void shouldReturnCommissionRateFromRegulation() {
        when(regulationProvider.getRegulation("COMMISSION_RATE"))
                .thenReturn(Optional.of(new Regulation("COMMISSION_RATE", "Commission Rate", "0.10", "Default commission rate.", true, 1)));

        CommissionRateDto result = regulationService.getCommissionRateDto();

        assertEquals(new BigDecimal("0.10"), result.getCommissionRate());
    }

    @Test
    void shouldReturnPointsConversionRatesFromRegulations() {
        when(regulationProvider.getRegulation("POINTS_EARN_CONVERSION_RATE"))
                .thenReturn(Optional.of(new Regulation("POINTS_EARN_CONVERSION_RATE", "Points Earn Conversion Rate", "10000", "Default earn rate.", true, 1)));
        when(regulationProvider.getRegulation("POINTS_REDEMPTION_CONVERSION_RATE"))
                .thenReturn(Optional.of(new Regulation("POINTS_REDEMPTION_CONVERSION_RATE", "Points Redemption Conversion Rate", "500", "Default redemption rate.", true, 1)));

        PointsConversionDto result = regulationService.getPointsConversionRatesDto();

        assertEquals(new BigDecimal("10000"), result.getEarnConversionRate());
        assertEquals(new BigDecimal("500"), result.getRedemptionConversionRate());
    }

    @Test
    void shouldThrowWhenCommissionRateIsInvalid() {
        when(regulationProvider.getRegulation("COMMISSION_RATE"))
                .thenReturn(Optional.of(new Regulation("COMMISSION_RATE", "Commission Rate", "invalid", "Bad value.", true, 1)));

        assertThrows(IllegalStateException.class, () -> regulationService.getCommissionRateDto());
    }
}
