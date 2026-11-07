package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HotelSearchCandidateMapperTest {
    @Test void generatedCandidateMappingReadsEligibilityAndPublishedFacetsWithoutDetailAssociations() {
        var mapper=Mappers.getMapper(HotelMapper.class);
        var rooms=Mappers.getMapper(RoomTypeMapper.class);
        ReflectionTestUtils.setField(rooms,"objectMapper",new ObjectMapper());
        mapper.roomTypeMapper=rooms;
        mapper.hotelPolicyRepository=mock(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class);
        mapper.questionRepository=mock(com.example.hotelservice.Question.repository.QuestionRepository.class);
        mapper.hotelAmenityCategoryRepository=mock(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class);
        mapper.hotelAmenityRepository=mock(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class);
        mapper.amenityCatalog=mock(com.example.hotelservice.Amenity.service.AmenityCatalogService.class);
        var room=spy(new RoomType());
        room.setId(UUID.randomUUID());room.setIsActive(true);room.setBasePrice(BigDecimal.valueOf(200000));room.setTotalRooms(3);
        room.setCapacity("{\"adults\":2,\"children\":0}");
        doThrow(new AssertionError("room media must wait for selected detail mapping")).when(room).getMediaAssets();
        doThrow(new AssertionError("room amenities are not eligibility input")).when(room).getAmenities();
        var inactive=spy(new RoomType());inactive.setIsActive(false);
        doThrow(new AssertionError("inactive capacity must not be parsed")).when(inactive).getCapacity();
        var hotel=spy(Hotel.builder().id(UUID.randomUUID()).starRating((short)4).roomTypes(List.of(room,inactive)).build());
        doThrow(new AssertionError("city is detail data")).when(hotel).getCity();
        doThrow(new AssertionError("hotel media is detail data")).when(hotel).getMediaAssets();
        doThrow(new AssertionError("address must wait for detail mapping")).when(hotel).getAddress();
        var result=mapper.toSearchCandidateResponse(hotel);
        assertThat(result.id()).isEqualTo(hotel.getId());assertThat(result.starRating()).isEqualTo((short)4);
        assertThat(result.roomTypes()).hasSize(1);
        var facts=result.roomTypes().get(0);
        assertThat(facts.id()).isEqualTo(room.getId());assertThat(facts.basePrice()).isEqualByComparingTo("200000");
        assertThat(facts.totalRooms()).isEqualTo(3);assertThat(facts.capacity().adults()).isEqualTo(2);assertThat(facts.capacity().children()).isZero();
        verifyNoInteractions(mapper.hotelPolicyRepository,mapper.questionRepository);
        verify(mapper.hotelAmenityCategoryRepository).findAllByHotelId(hotel.getId());
        verify(mapper.hotelAmenityRepository).findAllByCategoryHotelId(hotel.getId());
        doThrow(new AssertionError("Batch facts must not trigger the lazy room collection")).when(hotel).getRoomTypes();
        var batch=mapper.toSearchCandidateResponseWithFacts(hotel,List.of(room,inactive),List.of());
        assertThat(batch.id()).isEqualTo(hotel.getId());assertThat(batch.roomTypes()).hasSize(1);
        assertThat(batch.roomTypes().get(0).capacity().adults()).isEqualTo(2);
        assertThat(batch.amenityCategories()).isEmpty();
    }
}
