package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelOfferCandidatesTest {
    final RoomTypeRepository rooms=mock(RoomTypeRepository.class);
    final HotelAmenityRepository amenities=mock(HotelAmenityRepository.class);
    final HotelMapper mapper=mock(HotelMapper.class);
    final HotelOfferCandidates reader=new HotelOfferCandidates(rooms,amenities,mapper);
    @BeforeEach void setup() {
        when(mapper.toSearchCandidateResponseWithFacts(any(),anyList(),anyList())).thenAnswer(call->{
            Hotel source=call.getArgument(0);UUID id=source.getId();
            var result=mock(HotelResponse.class);when(result.id()).thenReturn(id);return result;
        });
    }
    Hotel hotel() { return Hotel.builder().id(UUID.randomUUID()).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build(); }
    RoomType room(Hotel hotel) {var room=new RoomType();room.setId(UUID.randomUUID());room.setHotelId(hotel.getId());return room;}
    HotelAmenityRepository.CandidateFacet facet(Hotel hotel,String label) {
        UUID category=UUID.randomUUID(),item=UUID.randomUUID();
        return new HotelAmenityRepository.CandidateFacet() {
            public UUID getHotelId(){return hotel.getId();}public UUID getCategoryId(){return category;}
            public String getCategoryTitle(){return "Actual category";}public UUID getItemId(){return item;}public String getTitle(){return label;}
        };
    }
    @Test void roomAndLiteralFacetFactsCannotMixAcrossHotelsOrTriggerLazyDetailReads() {
        var a=spy(hotel());var b=spy(hotel());var first=room(a);var second=room(b);
        doThrow(new AssertionError("No per-property lazy room read")).when(a).getRoomTypes();
        doThrow(new AssertionError("No per-property lazy room read")).when(b).getRoomTypes();
        when(rooms.findByHotelIdInAndIsActiveTrue(any())).thenReturn(List.of(second,first));
        when(amenities.findCandidateFacets(any())).thenReturn(List.of(facet(b,"Wi-Fi trả phí"),facet(a,"Pool, Indoor")));
        assertThat(reader.read(List.of(a,b,a))).extracting(HotelResponse::id).containsExactly(a.getId(),b.getId());
        verify(mapper).toSearchCandidateResponseWithFacts(eq(a),eq(List.of(first)),argThat(values->values.size()==1&&values.get(0).getItems().get(0).getTitle().equals("Pool, Indoor")));
        verify(mapper).toSearchCandidateResponseWithFacts(eq(b),eq(List.of(second)),argThat(values->values.size()==1&&values.get(0).getItems().get(0).getTitle().equals("Wi-Fi trả phí")));
        verify(mapper,never()).toPublicHotelResponse(any());
    }
    @Test void absentActiveRoomsAndFacetsStayEmptyWithoutInventedValues() {
        var a=hotel();assertThat(reader.read(List.of(a))).hasSize(1);
        verify(mapper).toSearchCandidateResponseWithFacts(a,List.of(),List.of());
    }
    @Test void emptyOrUnpublishedInputDoesNotReadPrivateFacts() {
        assertThat(reader.read(List.of())).isEmpty();
        var privateHotel=hotel();privateHotel.setApprovedStatus(HotelApprovalStatus.DRAFT);
        assertThatThrownBy(()->reader.read(List.of(privateHotel))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->reader.read(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rooms,amenities,mapper);
    }
    @Test void internalParameterChunksDoNotTruncateHotels() {
        var hotels=IntStream.range(0,501).mapToObj(i->hotel()).toList();
        assertThat(reader.read(hotels)).extracting(HotelResponse::id).containsExactlyElementsOf(hotels.stream().map(Hotel::getId).toList());
        verify(rooms,times(2)).findByHotelIdInAndIsActiveTrue(any());verify(amenities,times(2)).findCandidateFacets(any());
    }
}
