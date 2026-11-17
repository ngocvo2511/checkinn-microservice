package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Question.dto.QuestionResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Request-local facts for public hotel representations, read once for all hotels of one
 * response. Never cached across requests: approval and content stay as fresh as before.
 */
public record PublicHotelFacts(Set<UUID> approvedRoomIds,
                               Map<UUID, List<MediaAsset>> hotelMediaByHotel,
                               Map<UUID, List<MediaAsset>> roomMediaByRoom,
                               Map<UUID, List<PolicyResponse>> policiesByHotel,
                               Map<UUID, List<QuestionResponse>> faqsByHotel,
                               Map<UUID, List<AmenityResponse>> amenitiesByHotel) {

    public List<MediaAsset> hotelMedia(UUID hotelId) {
        return hotelId == null ? List.of() : hotelMediaByHotel.getOrDefault(hotelId, List.of());
    }

    public List<MediaAsset> roomMedia(UUID roomId) {
        return roomId == null ? List.of() : roomMediaByRoom.getOrDefault(roomId, List.of());
    }

    public List<PolicyResponse> policies(UUID hotelId) {
        return hotelId == null ? List.of() : policiesByHotel.getOrDefault(hotelId, List.of());
    }

    public List<QuestionResponse> faqs(UUID hotelId) {
        return hotelId == null ? List.of() : faqsByHotel.getOrDefault(hotelId, List.of());
    }

    public List<AmenityResponse> amenities(UUID hotelId) {
        return hotelId == null ? List.of() : amenitiesByHotel.getOrDefault(hotelId, List.of());
    }
}
