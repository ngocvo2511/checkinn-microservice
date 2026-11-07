package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Amenity.dto.response.AmenityItemResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.Amenity.repository.HotelAmenityRepository;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Internal published search facts. Public full representations are mapped after global paging. */
@Service
@RequiredArgsConstructor
public class HotelOfferCandidates {
    private static final int READ_BATCH_SIZE=500;
    private final RoomTypeRepository rooms;
    private final HotelAmenityRepository amenities;
    private final HotelMapper mapper;

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<HotelResponse> read(List<Hotel> hotels) {
        if(hotels==null)throw new IllegalArgumentException("Published hotel candidates are required");
        Map<UUID,Hotel> unique=new LinkedHashMap<>();
        for(var hotel:hotels) {
            if(hotel==null || hotel.getId()==null || !Boolean.TRUE.equals(hotel.getIsActive())
                    || hotel.getApprovedStatus()!=HotelApprovalStatus.APPROVED)
                throw new IllegalArgumentException("Published hotel candidates are required");
            unique.putIfAbsent(hotel.getId(),hotel);
        }
        var ids=new ArrayList<>(unique.keySet());
        var result=new ArrayList<HotelResponse>();
        for(int offset=0;offset<ids.size();offset+=READ_BATCH_SIZE) {
            var chunk=ids.subList(offset,Math.min(offset+READ_BATCH_SIZE,ids.size()));
            var byHotel=rooms.findByHotelIdInAndIsActiveTrue(chunk).stream().collect(Collectors.groupingBy(RoomType::getHotelId));
            Map<UUID,Map<UUID,AmenityResponse>> facets=new LinkedHashMap<>();
            for(var facet:amenities.findCandidateFacets(chunk)) {
                var categories=facets.computeIfAbsent(facet.getHotelId(),ignored->new LinkedHashMap<>());
                var category=categories.computeIfAbsent(facet.getCategoryId(),ignored->AmenityResponse.builder()
                        .id(facet.getCategoryId().toString()).title(facet.getCategoryTitle()).items(new ArrayList<>()).build());
                category.getItems().add(AmenityItemResponse.builder().id(facet.getItemId().toString()).title(facet.getTitle()).build());
            }
            for(var id:chunk)result.add(mapper.toSearchCandidateResponseWithFacts(unique.get(id),byHotel.getOrDefault(id,List.of()),
                    new ArrayList<>(facets.getOrDefault(id,Map.of()).values())));
        }
        return List.copyOf(result);
    }
}
