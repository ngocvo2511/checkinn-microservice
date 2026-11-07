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
    final com.example.hotelservice.Amenity.service.AmenityCatalogService amenities = mock(com.example.hotelservice.Amenity.service.AmenityCatalogService.class);
    final com.example.hotelservice.Review.service.ReviewSummaryService summaries=mock(com.example.hotelservice.Review.service.ReviewSummaryService.class);
    final HotelOfferCandidates candidates = new HotelOfferCandidates(mock(com.example.hotelservice.Room.repository.RoomTypeRepository.class),
            mock(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class),mapper);
    final HotelOfferController controller = new HotelOfferController(hotels,mapper,offers,locations,cities,amenities,summaries,candidates);
    @Test void reviewedCodeFilterUsesOneBulkSelectionBeforeDetailMapping() {
        var a=Hotel.builder().id(UUID.randomUUID()).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
        var b=Hotel.builder().id(UUID.randomUUID()).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
        when(hotels.searchByName("Fixture")).thenReturn(List.of(a,b));
        when(amenities.matchingHotels(anyCollection(),eq(List.of("WIFI_FREE")))).thenReturn(java.util.Set.of(b.getId()));
        var response=mock(com.example.hotelservice.Hotel.dto.response.HotelResponse.class);
        when(mapper.toPublicHotelResponse(b)).thenReturn(response);
        when(offers.match(eq(response),any())).thenReturn(java.util.Optional.empty());
        assertTrue(controller.search(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null,List.of("WIFI_FREE"))).isEmpty());
        verify(amenities,times(1)).matchingHotels(anyCollection(),eq(List.of("WIFI_FREE")));
        verify(amenities,never()).matches(any(),any());
        verify(mapper,never()).toPublicHotelResponse(a);
        verify(mapper).toPublicHotelResponse(b);
    }
    @Test void fullDetailMappingBudgetFollowsPageSizeAfterGlobalSort() {
        var entities=new java.util.ArrayList<Hotel>();
        var facts=new java.util.ArrayList<com.example.hotelservice.Review.dto.response.ReviewSummaryResponse>();
        for(int i=0;i<3;i++) {
            var id=UUID.randomUUID();
            var entity=Hotel.builder().id(id).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
            entities.add(entity);
            var dto=mock(com.example.hotelservice.Hotel.dto.response.HotelResponse.class);
            when(dto.id()).thenReturn(id);
            when(mapper.toPublicHotelResponse(entity)).thenReturn(dto);
            when(mapper.toSearchCandidateResponseWithFacts(eq(entity),anyList(),anyList())).thenReturn(dto);
            when(amenities.matches(id,null)).thenReturn(true);
            var price=java.math.BigDecimal.valueOf(300-i*100);
            when(offers.match(eq(dto),any(),anyMap())).thenReturn(java.util.Optional.of(new com.example.hotelservice.Hotel.dto.response.HotelOfferResponse.RoomOffer(UUID.randomUUID(),1,price,price,null,null,null,null)));
            facts.add(new com.example.hotelservice.Review.dto.response.ReviewSummaryResponse(id,0,null));
        }
        when(hotels.searchByName("Fixture")).thenReturn(entities);
        when(summaries.summarize(anyList())).thenReturn(facts);
        var result=controller.searchPage(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null,null),
                new com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria(0,1,"PRICE_ASC",null,null,null),
                new org.springframework.mock.web.MockHttpServletRequest());
        assertEquals(3,result.totalElements());
        assertEquals(entities.get(2).getId(),result.content().get(0).hotel().id());
        verify(mapper,times(1)).toPublicHotelResponse(any());
        verify(mapper).toPublicHotelResponse(entities.get(2));
    }
    @Test void literalAmenityWithCommaIsOneLabelRatherThanTwoFilters() throws Exception {
        UUID id=UUID.randomUUID();var entity=Hotel.builder().id(id).isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();
        var dto=mock(com.example.hotelservice.Hotel.dto.response.HotelResponse.class);
        when(dto.id()).thenReturn(id);
        when(dto.amenityCategories()).thenReturn(List.of(com.example.hotelservice.Amenity.dto.response.AmenityResponse.builder().items(List.of(com.example.hotelservice.Amenity.dto.response.AmenityItemResponse.builder().title("Pool, Indoor").build())).build()));
        when(hotels.searchByName("Fixture")).thenReturn(List.of(entity));when(amenities.matches(id,null)).thenReturn(true);when(mapper.toPublicHotelResponse(entity)).thenReturn(dto);
        when(mapper.toSearchCandidateResponseWithFacts(eq(entity),anyList(),anyList())).thenReturn(dto);
        when(offers.match(eq(dto),any(),anyMap())).thenReturn(java.util.Optional.of(new com.example.hotelservice.Hotel.dto.response.HotelOfferResponse.RoomOffer(UUID.randomUUID(),1,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,null,null,null,null)));
        when(summaries.summarize(List.of(id))).thenReturn(List.of(new com.example.hotelservice.Review.dto.response.ReviewSummaryResponse(id,0,null)));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/hotels/offers/page").param("hotelName","Fixture").param("amenities","Pool, Indoor"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(1));
    }
    @Test void pageCriteriaBindWithLegacyRoomCriteriaAndInvalidControlsReturn400() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(hotels.searchByName("Fixture")).thenReturn(List.of());
        mvc.perform(get("/hotels/offers/page").param("hotelName","Fixture").param("page","0").param("size","2").param("sort","PRICE_ASC").param("starRatings","4","5").param("guestRatings","8.5").param("amenities","Wi-Fi miễn phí"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sort").value("PRICE_ASC"));
        for(var pair:List.of(new String[]{"page","-1"},new String[]{"page","1.5"},new String[]{"size","51"},new String[]{"sort","POPULAR"},new String[]{"starRatings","6"},new String[]{"guestRatings","11"},new String[]{"amenities"," "}))
            mvc.perform(get("/hotels/offers/page").param("hotelName","Fixture").param(pair[0],pair[1])).andExpect(status().isBadRequest());
    }
    @Test void amenityCodesBindWithoutBreakingLegacyCriteriaAndEmptyOrUnknownIs400() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(hotels.searchByName("Fixture")).thenReturn(List.of());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("amenityCodes","WIFI_FREE","POOL")).andExpect(status().isOk());
        verify(amenities).validateCodes(List.of("WIFI_FREE","POOL"));
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("amenityCodes","")).andExpect(status().isBadRequest());
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("amenityCodes","Wi-Fi")).andExpect(status().isBadRequest());
        doThrow(new IllegalArgumentException("Unknown code")).when(amenities).validateCodes(List.of("UNKNOWN"));
        mvc.perform(get("/hotels/offers").param("hotelName","Fixture").param("amenityCodes","UNKNOWN")).andExpect(status().isBadRequest());
    }
    @Test void typesUseOrAndExcludeLegacyNullBeforeRoomMapping() {
        var criteria=new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,List.of("VILLA","HOTEL"),null);
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
        assertTrue(controller.search(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null,null)).isEmpty());
        h.setIsActive(true); h.setApprovedStatus(HotelApprovalStatus.DRAFT);
        assertTrue(controller.search(new HotelOfferCriteria(null,null,null,"Fixture",null,null,1,1,0,null,null,null,null)).isEmpty());
        verifyNoInteractions(mapper,offers);
    }
    @Test void provinceSearchUsesStableParentIdInsteadOfDisplayName() {
        UUID province=UUID.randomUUID(), city=UUID.randomUUID();
        City c=new City(); c.setId(city);
        when(locations.searchLocations("Fixture")).thenReturn(List.of(new LocationResponse(province,"Renamed",null,null,0,null,null,"PROVINCE")));
        when(cities.findByProvince_Id(province)).thenReturn(List.of(c));
        when(hotels.getByCities(java.util.Set.of(city))).thenReturn(List.of());
        assertTrue(controller.search(new HotelOfferCriteria(null,null,"Fixture",null,null,null,1,1,0,null,null,null,null)).isEmpty());
        verify(cities).findByProvince_Id(province); verify(hotels).getByCities(java.util.Set.of(city));
    }
    @Test void cityIdAvoidsNameResolution() {
        UUID city=UUID.randomUUID(); when(hotels.getByCities(java.util.Set.of(city))).thenReturn(List.of());
        controller.search(new HotelOfferCriteria(city,null,"Ambiguous",null,null,null,1,1,0,null,null,null,null));
        verifyNoInteractions(locations,cities); verify(hotels).getByCities(java.util.Set.of(city));
    }
    @Test void cityResponsePreservesParentIdentityAndName() {
        var parent=new com.example.hotelservice.City.entity.Province(); parent.setId(UUID.randomUUID()); parent.setName("Parent");
        City city=new City(); city.setId(UUID.randomUUID()); city.setName("City"); city.setProvince(parent);
        var cityMapper=org.mapstruct.factory.Mappers.getMapper(com.example.hotelservice.City.mapper.CityMapper.class);
        var response=cityMapper.toCityResponse(city);
        assertEquals(parent.getId(),response.parentId()); assertEquals("Parent",response.parentName());
    }
}
