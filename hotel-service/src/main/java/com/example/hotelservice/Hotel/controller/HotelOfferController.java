package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.City.service.LocationService;
import com.example.hotelservice.Hotel.dto.request.HotelOfferCriteria;
import com.example.hotelservice.Hotel.dto.response.HotelOfferResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Hotel.service.HotelOfferSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class HotelOfferController {
    private final HotelService hotels;
    private final HotelMapper mapper;
    private final HotelOfferSearchService offers;
    private final LocationService locations;
    private final CityRepository cities;
    private final com.example.hotelservice.Amenity.service.AmenityCatalogService amenities;
    private final com.example.hotelservice.Review.service.ReviewSummaryService summaries;
    private final com.example.hotelservice.Hotel.service.HotelOfferCandidates candidateReader;

    @ModelAttribute
    public void validateTypeCodes(jakarta.servlet.http.HttpServletRequest request) {
        String[] amenitiesRaw = request.getParameterValues("amenityCodes");
        if (amenitiesRaw != null) for (String code : amenitiesRaw) {
            if (!code.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("Mã tiện nghi không hợp lệ");
        }
        String[] raw = request.getParameterValues("accommodationTypes");
        if (raw != null) for (String code : raw) {
            if (code.isBlank()) throw new IllegalArgumentException("Loại hình lưu trú không hợp lệ");
            com.example.hotelservice.Hotel.enums.AccommodationType.parse(code);
        }
    }

    /** Offset pages are coherent within one snapshot, not pinned across separate requests. */
    @GetMapping("/offers/page")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public com.example.hotelservice.Hotel.dto.response.HotelOfferPageResponse searchPage(
            @ModelAttribute HotelOfferCriteria criteria,
            @ModelAttribute com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria page,
            jakarta.servlet.http.HttpServletRequest request) {
        // A literal amenity may contain a comma; collection conversion must not split it.
        String[] literalAmenities=request.getParameterValues("amenities");
        page=new com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria(page.page(),page.size(),page.sort(),
                page.starRatings(),page.guestRatings(),literalAmenities==null?null:Arrays.asList(literalAmenities));
        page.validate();
        var entities=candidateHotels(criteria);
        List<HotelOfferResponse> candidates=new ArrayList<>();
        var eligibility=candidateReader.read(new ArrayList<>(entities.values()));
        var inventory=offers.availabilityFor(eligibility,criteria);
        for(var facts:eligibility) {
            offers.match(facts,criteria,inventory).ifPresent(offer->candidates.add(new HotelOfferResponse(facts,offer)));
        }
        var reviewIds=candidates.stream().map(c->c.hotel().id()).distinct().toList();
        Map<UUID,com.example.hotelservice.Review.dto.response.ReviewSummaryResponse> facts=new HashMap<>();
        for(int offset=0;offset<reviewIds.size();offset+=com.example.hotelservice.Review.service.ReviewSummaryService.MAX_HOTELS)
            for(var summary:summaries.summarize(reviewIds.subList(offset,Math.min(offset+com.example.hotelservice.Review.service.ReviewSummaryService.MAX_HOTELS,reviewIds.size()))))facts.put(summary.hotelId(),summary);
        var selection=com.example.hotelservice.Hotel.service.HotelOfferPaging.page(candidates,facts,page);
        var content=selection.content().stream().map(selected->
                new com.example.hotelservice.Hotel.dto.response.HotelOfferPageResponse.RatedOffer(
                        mapper.toPublicHotelResponse(entities.get(selected.hotel().id())),selected.offer(),selected.reviews())).toList();
        return new com.example.hotelservice.Hotel.dto.response.HotelOfferPageResponse(content,selection.page(),selection.size(),
                selection.totalElements(),selection.totalPages(),selection.sort(),selection.availableAmenities());
    }

    /** Additive contract; legacy hotel lists keep their response shape. */
    @GetMapping("/offers")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<HotelOfferResponse> search(@ModelAttribute HotelOfferCriteria criteria) {
        List<HotelOfferResponse> result = new ArrayList<>();
        for (var hotel : candidateHotels(criteria).values()) {
            var response = mapper.toPublicHotelResponse(hotel);
            offers.match(response,criteria).ifPresent(offer -> result.add(new HotelOfferResponse(response,offer)));
        }
        return result;
    }

    private Map<UUID,Hotel> candidateHotels(HotelOfferCriteria criteria) {
        criteria.validate();
        amenities.validateCodes(criteria.amenityCodes());
        Set<UUID> cityIds = new HashSet<>();
        boolean locationFilter = criteria.cityId() != null || criteria.provinceId() != null ||
            (criteria.cityName() != null && !criteria.cityName().isBlank());
        if (criteria.cityId() != null) cityIds.add(criteria.cityId());
        else if (criteria.provinceId() != null) cities.findByProvince_Id(criteria.provinceId()).forEach(c -> cityIds.add(c.getId()));
        else if (locationFilter) {
            for (var location : locations.searchLocations(criteria.cityName())) {
                if ("CITY".equals(location.type())) cityIds.add(location.id());
                else if ("PROVINCE".equals(location.type())) cities.findByProvince_Id(location.id()).forEach(c -> cityIds.add(c.getId()));
            }
        }
        if (locationFilter && cityIds.isEmpty()) return Map.of();
        List<Hotel> candidates;
        if (criteria.hotelName() != null && !criteria.hotelName().isBlank()) candidates = hotels.searchByName(criteria.hotelName());
        else candidates = hotels.getByCities(cityIds);
        Map<UUID,Hotel> unique = new TreeMap<>();
        for (var hotel : candidates) {
            if (Boolean.TRUE.equals(hotel.getIsActive()) && hotel.getApprovedStatus() == HotelApprovalStatus.APPROVED
                    && criteria.matchesType(hotel.getAccommodationType())
                    && (!locationFilter || cityIds.contains(hotel.getCityId()))) unique.put(hotel.getId(),hotel);
        }
        unique.entrySet().removeIf(entry -> !amenities.matches(entry.getKey(),criteria.amenityCodes()));
        return unique;
    }
}
