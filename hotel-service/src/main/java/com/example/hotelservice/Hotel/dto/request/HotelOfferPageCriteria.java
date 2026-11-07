package com.example.hotelservice.Hotel.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record HotelOfferPageCriteria(Integer page,Integer size,String sort,List<Integer> starRatings,
        List<BigDecimal> guestRatings,List<String> amenities) {
    public int pageNumber(){return page==null?0:page;}
    public int pageSize(){return size==null?20:size;}
    public String sortOrder(){return sort==null?"DEFAULT":sort;}
    public void validate(){
        if(pageNumber()<0 || pageSize()<1 || pageSize()>50 || !Set.of("DEFAULT","PRICE_ASC","PRICE_DESC","RATING_DESC").contains(sortOrder()))
            throw new IllegalArgumentException("Invalid page, size or sort; size must be between 1 and 50");
        if(starRatings!=null && (starRatings.size()>5 || starRatings.stream().anyMatch(n->n==null || n<1 || n>5)))
            throw new IllegalArgumentException("Invalid star rating filter");
        if(guestRatings!=null && (guestRatings.size()>20 || guestRatings.stream().anyMatch(n->n==null || n.signum()<0 || n.compareTo(BigDecimal.TEN)>0)))
            throw new IllegalArgumentException("Invalid guest rating filter");
        if(amenities!=null && (amenities.size()>50 || amenities.stream().anyMatch(s->s==null || s.isBlank() || s.length()>255)))
            throw new IllegalArgumentException("Invalid amenity label filter");
    }
}
