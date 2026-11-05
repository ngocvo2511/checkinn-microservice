package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Amenity.entity.HotelAmenity;
import com.example.hotelservice.Amenity.entity.HotelAmenityCategory;
import com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelMapperFactsTest {
    @Test void publicAmenitiesRequireActiveCategoryAndItemButPrivateManagementKeepsBoth() {
        var mapper = org.mapstruct.factory.Mappers.getMapper(HotelMapper.class);
        var categories = mock(HotelAmenityCategoryRepository.class);
        var amenities = mock(HotelAmenityRepository.class);
        mapper.hotelAmenityCategoryRepository = categories;
        mapper.hotelAmenityRepository = amenities;
        var hotel = UUID.randomUUID();
        var active = HotelAmenityCategory.builder().id(UUID.randomUUID()).hotelId(hotel).title("Active category").isActive(true).build();
        var inactive = HotelAmenityCategory.builder().id(UUID.randomUUID()).hotelId(hotel).title("Inactive category").isActive(false).build();
        when(categories.findAllByHotelId(hotel)).thenReturn(List.of(active, inactive));
        when(amenities.findAllByCategoryHotelId(hotel)).thenReturn(List.of(
                HotelAmenity.builder().id(UUID.randomUUID()).category(active).title("Published amenity").isActive(true).build(),
                HotelAmenity.builder().id(UUID.randomUUID()).category(active).title("Inactive amenity").isActive(false).build(),
                HotelAmenity.builder().id(UUID.randomUUID()).category(inactive).title("Hidden category amenity").isActive(true).build()));
        var published = mapper.getPublicAmenities(hotel);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getItems()).extracting(item -> item.getTitle()).containsExactly("Published amenity");
        assertThat(mapper.getAmenities(hotel)).hasSize(2);
        assertThat(mapper.getAmenities(hotel).get(0).getItems()).hasSize(2);
    }
}
