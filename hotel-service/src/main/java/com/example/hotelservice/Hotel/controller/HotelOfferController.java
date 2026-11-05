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

    @ModelAttribute
    public void validateTypeCodes(jakarta.servlet.http.HttpServletRequest request) {
        String[] raw = request.getParameterValues("accommodationTypes");
        if (raw != null) for (String code : raw) {
            if (code.isBlank()) throw new IllegalArgumentException("Loại hình lưu trú không hợp lệ");
            com.example.hotelservice.Hotel.enums.AccommodationType.parse(code);
        }
    }

    /** Additive contract; legacy hotel lists keep their response shape. */
    @GetMapping("/offers")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<HotelOfferResponse> search(@ModelAttribute HotelOfferCriteria criteria) {
        criteria.validate();
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
        if (locationFilter && cityIds.isEmpty()) return List.of();
        List<Hotel> candidates;
        if (criteria.hotelName() != null && !criteria.hotelName().isBlank()) candidates = hotels.searchByName(criteria.hotelName());
        else candidates = cityIds.stream().flatMap(id -> hotels.getByCity(id).stream()).toList();
        Map<UUID,Hotel> unique = new TreeMap<>();
        for (var hotel : candidates) {
            if (Boolean.TRUE.equals(hotel.getIsActive()) && hotel.getApprovedStatus() == HotelApprovalStatus.APPROVED
                    && criteria.matchesType(hotel.getAccommodationType())
                    && (!locationFilter || cityIds.contains(hotel.getCityId()))) unique.put(hotel.getId(),hotel);
        }
        List<HotelOfferResponse> result = new ArrayList<>();
        for (var hotel : unique.values()) {
            var response = mapper.toPublicHotelResponse(hotel);
            offers.match(response,criteria).ifPresent(offer -> result.add(new HotelOfferResponse(response,offer)));
        }
        return result;
    }
}
