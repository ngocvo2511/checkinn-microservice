package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.controller.HotelOfferController;
import com.example.hotelservice.Hotel.dto.request.HotelOfferCriteria;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.City.service.LocationService;
import com.example.hotelservice.City.dto.response.LocationResponse;
import com.example.hotelservice.City.entity.City;
import com.example.hotelservice.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelOfferControllerTest {
    final HotelService hotels = mock(HotelService.class);
    final HotelMapper mapper = mock(HotelMapper.class);
    final HotelOfferSearchService offers = mock(HotelOfferSearchService.class);
    final LocationService locations = mock(LocationService.class);
    final CityRepository cities = mock(CityRepository.class);
    final HotelOfferController controller = new HotelOfferController(hotels,mapper,offers,locations,cities);
    @Test void typesUseOrAndExcludeLegacyNullBeforeRoomMapping() {
        var criteria=new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,List.of("VILLA","HOTEL"));
        criteria.validate();
        assertTrue(criteria.matchesType(com.example.hotelservice.Hotel.enums.AccommodationType.VILLA));
        assertTrue(criteria.matchesType(com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL));
        assertFalse(criteria.matchesType(com.example.hotelservice.Hotel.enums.AccommodationType.APARTMENT));
        assertFalse(criteria.matchesType(null));
        Hotel h=Hotel.builder().id(UUID.randomUUID()).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
        when(hotels.searchByName("Fixture")).thenReturn(List.of(h));
        assertTrue(controller.search(criteria).isEmpty());verifyNoInteractions(mapper,offers);
    }
    @Test void invalidAccommodationCodeIs400BeforeCandidateLookup() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("accommodationTypes","ALIEN")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("accommodationTypes", "")).andExpect(status().isBadRequest());
        verifyNoInteractions(hotels,mapper,offers);
    }
    @Test void dateAndIntegerParametersBindAndValidationIsHttp400() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(hotels.searchByName("Fixture")).thenReturn(List.of());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("checkIn","2026-12-01").param("checkOut","2026-12-02").param("rooms","2")).andExpect(status().isOk());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("checkIn","2026-12-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("rooms","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("minPrice","300").param("maxPrice","100")).andExpect(status().isBadRequest());
    }
    @Test void publicationIsEnforcedEvenIfCandidateListContainsPrivateHotel() {
        var h=new Hotel(); h.setId(UUID.randomUUID()); h.setIsActive(false); h.setApprovedStatus(HotelApprovalStatus.APPROVED);
        when(hotels.searchByName("Fixture")).thenReturn(List.of(h));
        assertTrue(controller.search(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null)).isEmpty());
        h.setIsActive(true); h.setApprovedStatus(HotelApprovalStatus.DRAFT);
        assertTrue(controller.search(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null)).isEmpty());
        verifyNoInteractions(mapper,offers);
    }
    @Test void provinceSearchUsesStableParentIdInsteadOfDisplayName() {
        UUID province=UUID.randomUUID(), city=UUID.randomUUID();
        City c=new City(); c.setId(city);
        when(locations.searchLocations("Fixture")).thenReturn(List.of(new LocationResponse(province,"Renamed",null,null,0,null,null,"PROVINCE")));
        when(cities.findByProvince_Id(province)).thenReturn(List.of(c));
        when(hotels.getByCity(city)).thenReturn(List.of());
        assertTrue(controller.search(new HotelOfferCriteria(null,null,"Fixture",null,null,null,1,1,0,null,null,null)).isEmpty());
        verify(cities).findByProvince_Id(province); verify(hotels).getByCity(city);
    }
    @Test void cityIdAvoidsNameResolution() {
        UUID city=UUID.randomUUID(); when(hotels.getByCity(city)).thenReturn(List.of());
        controller.search(new HotelOfferCriteria(city,null,"Ambiguous",null,null,null,1,1,0,null,null,null));
        verifyNoInteractions(locations,cities); verify(hotels).getByCity(city);
    }
    @Test void cityResponsePreservesParentIdentityAndName() {
        var parent=new com.example.hotelservice.City.entity.Province(); parent.setId(UUID.randomUUID()); parent.setName("Parent");
        City city=new City(); city.setId(UUID.randomUUID()); city.setName("City"); city.setProvince(parent);
        var cityMapper=org.mapstruct.factory.Mappers.getMapper(com.example.hotelservice.City.mapper.CityMapper.class);
        var response=cityMapper.toCityResponse(city);
        assertEquals(parent.getId(),response.parentId()); assertEquals("Parent",response.parentName());
    }
}
