package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria;
import com.example.hotelservice.Hotel.dto.response.*;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Amenity.dto.response.*;
import com.example.hotelservice.Review.dto.response.ReviewSummaryResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HotelOfferPagingTest {
    private final Map<UUID,ReviewSummaryResponse> summaries=new HashMap<>();
    private HotelOfferResponse row(int id,int price,int star,String label,Double rating){
        UUID uuid=UUID.fromString("11111111-1111-4111-8111-"+String.format("%012d",id));
        var labels=List.of(AmenityResponse.builder().title("Actual property labels").items(List.of(AmenityItemResponse.builder().title(label).build())).build());
        var amount=BigDecimal.valueOf(price);
        var hotel=new HotelResponse(uuid,null,null,"Synthetic hotel "+id,null,(short)star,null,null,null,List.of(),List.of(),labels,true,HotelApprovalStatus.APPROVED,null,null,null,amount,List.of(),List.of(),null,null);
        summaries.put(uuid,new ReviewSummaryResponse(uuid,rating==null?0:2,rating==null?null:BigDecimal.valueOf(rating)));
        return new HotelOfferResponse(hotel,new HotelOfferResponse.RoomOffer(UUID.randomUUID(),1,amount,amount,null,null,null,null));
    }
    private HotelOfferPageCriteria options(int page,int size,String sort){return new HotelOfferPageCriteria(page,size,sort,null,null,null);}
    @Test void allFiltersPrecedePaginationAndTotalWithGlobalAmenityOptions(){
        var rows=new ArrayList<HotelOfferResponse>();
        for(int i=1;i<=30;i++)rows.add(row(i,100, i==30?5:4,i==30?"Checked":"Other",i==30?9.0:6.0));
        var result=HotelOfferPaging.page(rows,summaries,new HotelOfferPageCriteria(0,1,"PRICE_ASC",List.of(5),List.of(new BigDecimal("8.5")),List.of("Checked")));
        assertEquals(1,result.totalElements());assertEquals(1,result.totalPages());assertEquals(rows.getLast().hotel().id(),result.content().getFirst().hotel().id());
        assertEquals(List.of("Checked","Other"),result.availableAmenities());
    }
    @Test void pricesAreGlobalAndTiesUseAscendingStableIdInBothDirections(){
        var a=row(1,100,5,"A",8.0);var b=row(2,100,5,"A",8.0);var c=row(3,200,5,"A",8.0);var input=List.of(c,b,a);
        var first=HotelOfferPaging.page(input,summaries,options(0,2,"PRICE_ASC"));
        assertEquals(List.of(a.hotel().id(),b.hotel().id()),first.content().stream().map(r->r.hotel().id()).toList());assertEquals(3,first.totalElements());assertEquals(2,first.totalPages());
        assertEquals(c.hotel().id(),HotelOfferPaging.page(input,summaries,options(1,2,"PRICE_ASC")).content().getFirst().hotel().id());
        assertEquals(List.of(c.hotel().id(),a.hotel().id(),b.hotel().id()),HotelOfferPaging.page(input,summaries,options(0,3,"PRICE_DESC")).content().stream().map(r->r.hotel().id()).toList());
    }
    @Test void ratingSortKeepsUnratedLastAndNeverAssignsZeroScore(){
        var empty=row(1,100,5,"A",null);var low=row(2,100,5,"A",6.0);var high=row(3,100,5,"A",9.0);
        var page=HotelOfferPaging.page(List.of(empty,low,high),summaries,options(0,20,"RATING_DESC"));
        assertEquals(List.of(high.hotel().id(),low.hotel().id(),empty.hotel().id()),page.content().stream().map(r->r.hotel().id()).toList());assertNull(page.content().getLast().reviews().averageRating());
    }
    @Test void defaultOrderAndLastOutOfRangePageHaveCoherentMetadataWithoutOverflow(){
        var a=row(1,100,5,"A",null);var b=row(2,100,5,"A",null);
        assertEquals(a.hotel().id(),HotelOfferPaging.page(List.of(b,a),summaries,new HotelOfferPageCriteria(null,null,null,null,null,null)).content().getFirst().hotel().id());
        var empty=HotelOfferPaging.page(List.of(b,a),summaries,options(Integer.MAX_VALUE,50,"DEFAULT"));assertTrue(empty.content().isEmpty());assertEquals(2,empty.totalElements());assertEquals(1,empty.totalPages());
    }
    @Test void noMatchesDoesNotFabricatePageOrReviewFacts(){
        var result=HotelOfferPaging.page(List.of(),Map.of(),options(0,20,"DEFAULT"));assertEquals(0,result.totalElements());assertEquals(0,result.totalPages());assertTrue(result.content().isEmpty());
        var h=row(1,100,5,"A",null);var filtered=HotelOfferPaging.page(List.of(h),summaries,new HotelOfferPageCriteria(0,20,"DEFAULT",null,List.of(BigDecimal.ZERO),null));assertEquals(0,filtered.totalElements());
    }
    @Test void missingSummaryCannotSilentlyRemoveHotelOrClaimItHasNoReviews(){
        var h=row(1,100,5,"A",null);assertThrows(IllegalStateException.class,()->HotelOfferPaging.page(List.of(h),Map.of(),options(0,20,"DEFAULT")));
    }
    @Test void invalidPaginationFiltersAreRejected(){
        for(var c:List.of(options(-1,20,"DEFAULT"),options(0,0,"DEFAULT"),options(0,51,"DEFAULT"),options(0,20,"POPULAR"),new HotelOfferPageCriteria(0,20,"DEFAULT",List.of(6),null,null),new HotelOfferPageCriteria(0,20,"DEFAULT",null,List.of(new BigDecimal("10.1")),null),new HotelOfferPageCriteria(0,20,"DEFAULT",null,null,List.of(" "))))assertThrows(IllegalArgumentException.class,c::validate);
    }
}
