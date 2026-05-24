package com.example.regulationsservice.service;

import com.example.regulationsservice.dto.RegulationDto;
import com.example.regulationsservice.model.Regulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegulationServiceTest {

    private RegulationProvider regulationProvider;
    private RegulationService regulationService;

    @BeforeEach
    void setUp() {
        regulationProvider = mock(RegulationProvider.class);
        regulationService = new RegulationService(regulationProvider);
    }

    @Test
    void listRegulationsMapsProviderModelsToDtos() {
        when(regulationProvider.getAllRegulations()).thenReturn(List.of(
                new Regulation("COMMISSION_RATE", "Commission", "0.1", "desc", true, 2)
        ));

        List<RegulationDto> result = regulationService.listRegulations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRegulationKey()).isEqualTo("COMMISSION_RATE");
        assertThat(result.get(0).getValue()).isEqualTo("0.1");
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    void upsertRegulationDelegatesSaveWithRequestedKeyAndChangedBy() {
        RegulationDto request = new RegulationDto();
        request.setName("Commission");
        request.setValue("0.12");
        request.setDescription("Updated");
        request.setActive(true);
        request.setVersion(3);

        when(regulationProvider.saveRegulation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("admin")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegulationDto saved = regulationService.upsertRegulation("COMMISSION_RATE", request, "admin");

        assertThat(saved.getRegulationKey()).isEqualTo("COMMISSION_RATE");
        assertThat(saved.getValue()).isEqualTo("0.12");
        verify(regulationProvider).saveRegulation(org.mockito.ArgumentMatchers.any(Regulation.class), org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    void getCommissionRateRejectsInactiveRegulation() {
        when(regulationProvider.getRegulation("COMMISSION_RATE")).thenReturn(Optional.of(
                new Regulation("COMMISSION_RATE", "Commission", "0.1", "desc", false, 1)
        ));

        assertThatThrownBy(() -> regulationService.getCommissionRateDto())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active regulation not found");
    }

    @Test
    void getPointsConversionRatesRejectsInvalidDecimalValue() {
        when(regulationProvider.getRegulation("POINTS_EARN_CONVERSION_RATE")).thenReturn(Optional.of(
                new Regulation("POINTS_EARN_CONVERSION_RATE", "Earn", "abc", "desc", true, 1)
        ));

        assertThatThrownBy(() -> regulationService.getPointsConversionRatesDto())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid decimal");
    }

    @Test
    void listSnapshotsReturnsEmptyListWhenProviderFails() {
        when(regulationProvider.getSnapshots()).thenThrow(new RuntimeException("storage unavailable"));

        assertThat(regulationService.listSnapshots()).isEmpty();
    }
}
