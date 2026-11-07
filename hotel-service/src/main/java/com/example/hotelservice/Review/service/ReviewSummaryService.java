package com.example.hotelservice.Review.service;

import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Review.dto.response.ReviewSummaryResponse;
import com.example.hotelservice.Review.enums.ReviewStatus;
import com.example.hotelservice.Review.repository.HotelReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewSummaryService {
    public static final int MAX_HOTELS=100;
    private final HotelReviewRepository reviews;

    @Transactional(readOnly=true)
    public List<ReviewSummaryResponse> summarize(List<UUID> hotelIds) {
        if(hotelIds==null || hotelIds.isEmpty() || hotelIds.size()>MAX_HOTELS || hotelIds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Provide between 1 and 100 hotel identifiers");
        List<UUID> unique=new ArrayList<>(new LinkedHashSet<>(hotelIds));
        Map<UUID,ReviewSummaryResponse> result=new HashMap<>();
        for(var row:reviews.summarizePublished(unique,ReviewStatus.PUBLISHED,HotelApprovalStatus.APPROVED)) {
            BigDecimal average=row.getAverageRating()==null ? null : BigDecimal.valueOf(row.getAverageRating()).setScale(2,RoundingMode.HALF_UP);
            result.put(row.getHotelId(),new ReviewSummaryResponse(row.getHotelId(),row.getTotalReviews(),average));
        }
        // Omit absent/private properties without claiming that they have zero reviews.
        return unique.stream().filter(result::containsKey).map(result::get).toList();
    }
}
