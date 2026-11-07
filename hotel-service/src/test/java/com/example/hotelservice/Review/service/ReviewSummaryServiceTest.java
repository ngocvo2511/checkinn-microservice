package com.example.hotelservice.Review.service;

import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Review.enums.ReviewStatus;
import com.example.hotelservice.Review.repository.HotelReviewRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewSummaryServiceTest {
    private final HotelReviewRepository repository=mock(HotelReviewRepository.class);
    private final ReviewSummaryService service=new ReviewSummaryService(repository);
    private HotelReviewRepository.PublicSummary row(UUID id,long count,Double average) {
        return new HotelReviewRepository.PublicSummary() {
            public UUID getHotelId(){return id;}
            public long getTotalReviews(){return count;}
            public Double getAverageRating(){return average;}
        };
    }
    @Test void boundedBatchIsOneAggregateQueryWithoutReviewHydrationOrProfileRpc() {
        var ids=java.util.stream.IntStream.range(0,100).mapToObj(i->UUID.randomUUID()).toList();
        when(repository.summarizePublished(ids,ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED)).thenReturn(List.of(row(ids.get(0),2,8.555)));
        var result=service.summarize(ids);
        assertEquals(new BigDecimal("8.56"),result.getFirst().averageRating());assertEquals(2,result.getFirst().totalReviews());
        verify(repository).summarizePublished(ids,ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED);verifyNoMoreInteractions(repository);
    }
    @Test void knownNoReviewsIsZeroCountAndNullAverage() {
        var id=UUID.randomUUID();when(repository.summarizePublished(List.of(id),ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED)).thenReturn(List.of(row(id,0,null)));
        var result=service.summarize(List.of(id)).getFirst();assertEquals(0,result.totalReviews());assertNull(result.averageRating());
    }
    @Test void duplicateIdsAreDeduplicatedAndReturnedInInputOrder() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();
        when(repository.summarizePublished(List.of(a,b),ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED)).thenReturn(List.of(row(b,0,null),row(a,2,8.0)));
        assertEquals(List.of(a,b),service.summarize(List.of(a,b,a)).stream().map(r->r.hotelId()).toList());
        verify(repository).summarizePublished(List.of(a,b),ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED);verifyNoMoreInteractions(repository);
    }
    @Test void privateAndMissingHotelsAreOmittedWithoutInventingNoReviewFacts() {
        var id=UUID.randomUUID();when(repository.summarizePublished(List.of(id),ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED)).thenReturn(List.of());
        assertTrue(service.summarize(List.of(id)).isEmpty());
    }
    @Test void invalidAndOversizedInputsNeverQueryStorage() {
        assertThrows(IllegalArgumentException.class,()->service.summarize(null));
        assertThrows(IllegalArgumentException.class,()->service.summarize(List.of()));
        assertThrows(IllegalArgumentException.class,()->service.summarize(Arrays.asList(UUID.randomUUID(),null)));
        assertThrows(IllegalArgumentException.class,()->service.summarize(Collections.nCopies(101,UUID.randomUUID())));
        verifyNoInteractions(repository);
    }
    @Test void storageFailureCannotBecomeAnEmptySuccess() {
        var id=UUID.randomUUID();when(repository.summarizePublished(anyList(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private storage error"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.summarize(List.of(id)));
    }
}
