package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.dto.response.HotelSummaryResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HotelSummaryService {
    public static final int MAX_HOTELS = 50;
    private final HotelRepository hotels;
    private final MediaAssetRepository media;

    @Transactional(readOnly = true)
    public List<HotelSummaryResponse> summarize(List<UUID> hotelIds) {
        if (hotelIds == null || hotelIds.isEmpty() || hotelIds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Provide between 1 and 50 hotel identifiers");
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(hotelIds));
        if (unique.size() > MAX_HOTELS)
            throw new IllegalArgumentException("Provide between 1 and 50 hotel identifiers");

        List<Hotel> published = hotels.findByIdInAndIsActiveTrueAndApprovedStatus(unique, HotelApprovalStatus.APPROVED);
        if (published.isEmpty()) return List.of();
        Map<UUID, String> covers = coverImages(published.stream().map(Hotel::getId).toList());
        Map<UUID, HotelSummaryResponse> byId = new HashMap<>();
        for (Hotel hotel : published) {
            var city = hotel.getCity();
            var province = city == null ? null : city.getProvince();
            byId.put(hotel.getId(), new HotelSummaryResponse(hotel.getId(), hotel.getName(),
                    city == null ? null : city.getName(), province == null ? null : province.getName(),
                    hotel.getStarRating(), covers.get(hotel.getId())));
        }
        return unique.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    private Map<UUID, String> coverImages(List<UUID> hotelIds) {
        Map<UUID, MediaAsset> chosen = new HashMap<>();
        Comparator<MediaAsset> preference = Comparator
                .comparing((MediaAsset m) -> !Boolean.TRUE.equals(m.getIsThumbnail()))
                .thenComparing(MediaAsset::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
        for (MediaAsset asset : media.findByTargetTypeAndTargetIdIn(MediaTargetType.HOTEL, hotelIds)) {
            chosen.merge(asset.getTargetId(), asset, (a, b) -> preference.compare(a, b) <= 0 ? a : b);
        }
        Map<UUID, String> urls = new HashMap<>();
        chosen.forEach((id, asset) -> urls.put(id, asset.getUrl()));
        return urls;
    }
}
