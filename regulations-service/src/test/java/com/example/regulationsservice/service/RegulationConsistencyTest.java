package com.example.regulationsservice.service;

import com.example.regulationsservice.messaging.RegulationEventPublisher;
import com.example.regulationsservice.model.*;
import com.example.regulationsservice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegulationConsistencyTest {
    final RegulationRepository rows=mock(RegulationRepository.class);
    final RegulationSnapshotRepository snapshots=mock(RegulationSnapshotRepository.class);
    final RegulationEventPublisher events=mock(RegulationEventPublisher.class);
    final RegulationCacheProvider provider=new RegulationCacheProvider(rows,snapshots,events);

    @Test void readsUseCommittedDatabaseAcrossInstancesInsteadOfAnUnversionedLocalCache() {
        var row=new Regulation("SYNTHETIC_RATE","Rate","2",null,true,3);
        when(rows.findByRegulationKey("SYNTHETIC_RATE")).thenReturn(Optional.of(row));
        when(rows.findAll()).thenReturn(List.of(row));
        assertThat(provider.getRegulation("SYNTHETIC_RATE")).containsSame(row);
        assertThat(provider.getAllRegulations()).containsExactly(row);
    }
    @Test void staleFormVersionCannotOverwriteANewerRegulation() {
        when(rows.findByRegulationKey("SYNTHETIC_RATE")).thenReturn(Optional.of(
                new Regulation("SYNTHETIC_RATE","Rate","2",null,true,3)));
        assertThatThrownBy(()->provider.saveRegulation(new Regulation("SYNTHETIC_RATE","Rate","9",null,true,2),"synthetic-admin"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(rows,never()).save(any());verifyNoInteractions(snapshots,events);
    }
    @Test void snapshotRoundTripsUnicodeQuotesBackslashesAndNewlines() throws Exception {
        var row=new Regulation("SYNTHETIC_RATE","Tên \"có\"\nquote","2","Dòng\\một\n\"hai\"",true,1);
        when(rows.save(any())).thenAnswer(i->i.getArgument(0));
        when(rows.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        provider.saveRegulation(row,"synthetic-admin");
        var saved=org.mockito.ArgumentCaptor.forClass(RegulationSnapshot.class);
        verify(snapshots).save(saved.capture());
        var json=new ObjectMapper().readTree(saved.getValue().getSnapshotData());
        assertThat(json.get("name").asText()).isEqualTo(row.getName());
        assertThat(json.get("description").asText()).isEqualTo(row.getDescription());
    }
    @Test void historyOutageIsNotAnEmptySuccessfulAuditHistory() {
        var source=mock(RegulationProvider.class);
        when(source.getSnapshots()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-private-db-detail"));
        assertThatThrownBy(()->new RegulationService(source).listSnapshots())
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
