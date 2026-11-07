package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria;
import com.example.hotelservice.Hotel.dto.response.HotelOfferResponse;
import com.example.hotelservice.Hotel.dto.response.HotelOfferPageResponse;
import com.example.hotelservice.Hotel.dto.response.HotelOfferPageResponse.RatedOffer;
import com.example.hotelservice.Review.dto.response.ReviewSummaryResponse;
import java.math.BigDecimal;
import java.util.*;

/** Global filters and deterministic ordering precede the offset boundary. */
public final class HotelOfferPaging {
    private HotelOfferPaging(){}
    public static HotelOfferPageResponse page(List<HotelOfferResponse> candidates,
            Map<UUID,ReviewSummaryResponse> summaries,HotelOfferPageCriteria criteria) {
        criteria.validate();
        var facets=new TreeSet<String>();
        for(var candidate:candidates)facets.addAll(labels(candidate));
        var all=new ArrayList<RatedOffer>();
        for(var candidate:candidates){
            var hotel=candidate.hotel();var reviews=summaries.get(hotel.id());
            if(reviews==null)throw new IllegalStateException("Published review summary missing from search snapshot");
            if(criteria.starRatings()!=null && !criteria.starRatings().isEmpty() &&
                    (hotel.starRating()==null || !criteria.starRatings().contains(hotel.starRating().intValue())))continue;
            if(criteria.amenities()!=null && !labels(candidate).containsAll(criteria.amenities()))continue;
            if(criteria.guestRatings()!=null && !criteria.guestRatings().isEmpty() &&
                    (reviews.totalReviews()==0 || reviews.averageRating()==null || criteria.guestRatings().stream().noneMatch(n->reviews.averageRating().compareTo(n)>=0)))continue;
            all.add(new RatedOffer(hotel,candidate.offer(),reviews));
        }
        Comparator<RatedOffer> byId=Comparator.comparing(r->r.hotel().id().toString());
        Comparator<RatedOffer> comparator=switch(criteria.sortOrder()){
            case "PRICE_ASC" -> Comparator.comparing((RatedOffer r)->r.offer().unitPrice()).thenComparing(byId);
            case "PRICE_DESC" -> Comparator.comparing((RatedOffer r)->r.offer().unitPrice(),Comparator.reverseOrder()).thenComparing(byId);
            case "RATING_DESC" -> Comparator.comparing((RatedOffer r)->r.reviews().totalReviews()==0?null:r.reviews().averageRating(),Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())).thenComparing(byId);
            default -> byId;
        };
        all.sort(comparator);
        long offset=(long)criteria.pageNumber()*criteria.pageSize();
        List<RatedOffer> content=offset>=all.size()?List.of():List.copyOf(all.subList((int)offset,(int)Math.min(offset+criteria.pageSize(),all.size())));
        int pages=(int)(((long)all.size()+criteria.pageSize()-1)/criteria.pageSize());
        return new HotelOfferPageResponse(content,criteria.pageNumber(),criteria.pageSize(),all.size(),pages,criteria.sortOrder(),List.copyOf(facets));
    }
    private static Set<String> labels(HotelOfferResponse candidate){
        Set<String> result=new HashSet<>();
        if(candidate.hotel().amenityCategories()!=null)for(var category:candidate.hotel().amenityCategories())
            if(category.getItems()!=null)for(var item:category.getItems())if(item.getTitle()!=null&&!item.getTitle().isBlank())result.add(item.getTitle().trim());
        return result;
    }
}
