package com.example.hotelservice.grpc;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.MediaAsset.service.MediaService;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class HotelGrpcServiceImpl extends HotelGrpcServiceGrpc.HotelGrpcServiceImplBase {

    private final HotelService hotelService;
    private final RoomTypeService roomTypeService;
    private final MediaService mediaAssetService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // GET HOTEL BY ID
    // ============================================================
    @Override
    public void getHotelById(GetHotelByIdRequest request,
                             StreamObserver<GetHotelByIdResponse> responseObserver) {

        try {
            UUID hotelId = UUID.fromString(request.getHotelId());

            Hotel hotel = hotelService.getById(hotelId);
            List<RoomType> roomTypes = roomTypeService.getByHotel(hotelId);
            List<MediaAsset> images = mediaAssetService.getByTarget(hotelId, MediaTargetType.HOTEL);

            GetHotelByIdResponse.Builder builder = GetHotelByIdResponse.newBuilder()
                    .setId(hotel.getId().toString())
                    .setOwnerId(hotel.getOwnerId().toString())
                    .setName(nvl(hotel.getName()))
                    .setDescription(nvl(hotel.getDescription()))
                    .setStarRating(hotel.getStarRating() == null ? 0 : hotel.getStarRating())
                    .setIsActive(Boolean.TRUE.equals(hotel.getIsActive()))
                    .setApprovedStatus(hotel.getApprovedStatus().name());

            // address json → proto
            if (hotel.getAddress() != null) {
                builder.setAddress(mapHotelAddress(hotel.getAddress()));
            }

            // add roomTypes basic
            for (RoomType rt : roomTypes) {
                builder.addRoomTypes(mapRoomTypeBasic(rt));
            }

            // add images
            for (MediaAsset img : images) {
                builder.addImages(mapMedia(img));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // GET ROOM TYPES BY HOTEL
    // ============================================================
    @Override
    public void getRoomTypesByHotel(GetRoomTypesByHotelRequest request,
                                    StreamObserver<GetRoomTypesByHotelResponse> responseObserver) {

        try {
            UUID hotelId = UUID.fromString(request.getHotelId());
            List<RoomType> roomTypes = roomTypeService.getByHotel(hotelId);

            GetRoomTypesByHotelResponse.Builder builder = GetRoomTypesByHotelResponse.newBuilder();

            for (RoomType rt : roomTypes) {
                List<MediaAsset> images = mediaAssetService.getByTarget(rt.getId(), MediaTargetType.ROOM_TYPE);
                builder.addRoomTypes(mapRoomTypeDto(rt, images));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // GET ROOM TYPE BY ID
    // ============================================================
    @Override
    public void getRoomTypeById(GetRoomTypeByIdRequest request,
                                StreamObserver<GetRoomTypeByIdResponse> responseObserver) {

        try {
            UUID rtId = UUID.fromString(request.getRoomTypeId());
            RoomType roomType = roomTypeService.getById(rtId);

            List<MediaAsset> images = mediaAssetService.getByTarget(rtId, MediaTargetType.ROOM_TYPE);

            RoomTypeDto dto = mapRoomTypeDto(roomType, images);

            GetRoomTypeByIdResponse response = GetRoomTypeByIdResponse.newBuilder()
                    .setRoomType(dto)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // MAPPING HELPERS
    // ============================================================

    private HotelAddress mapHotelAddress(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);

        return HotelAddress.newBuilder()
                .setStreet(nvl(node.path("street").asText()))
                .setCity(nvl(node.path("city").asText()))
                .setCountry(nvl(node.path("country").asText()))
                .setLatitude(node.path("latitude").asDouble(0))
                .setLongitude(node.path("longitude").asDouble(0))
                .build();
    }

    private RoomTypeBasic mapRoomTypeBasic(RoomType rt) {
        return RoomTypeBasic.newBuilder()
                .setId(rt.getId().toString())
                .setName(nvl(rt.getName()))
                .setBasePrice(rt.getBasePrice().doubleValue())
                .build();
    }

    private RoomTypeDto mapRoomTypeDto(RoomType rt, List<MediaAsset> images) throws Exception {

        RoomTypeDto.Builder builder = RoomTypeDto.newBuilder()
                .setId(rt.getId().toString())
                .setHotelId(rt.getHotelId().toString())
                .setName(nvl(rt.getName()))
                .setBasePrice(rt.getBasePrice().doubleValue())
                .setIsActive(rt.getIsActive());

        // capacity
        if (rt.getCapacity() != null) {
            builder.setCapacity(mapCapacity(rt.getCapacity()));
        }

        // amenities
        if (rt.getAmenities() != null) {
            List<String> amenities = objectMapper.readValue(rt.getAmenities(), List.class);
            builder.addAllAmenities(amenities);
        }

        for (MediaAsset img : images) {
            builder.addImages(mapMedia(img));
        }

        return builder.build();
    }

    private Capacity mapCapacity(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);

        return Capacity.newBuilder()
                .setAdults(node.path("adults").asInt(0))
                .setChildren(node.path("children").asInt(0))
                .build();
    }

    private com.example.hotelservice.grpc.MediaAsset mapMedia(com.example.hotelservice.MediaAsset.entity.MediaAsset m) {
        return com.example.hotelservice.grpc.MediaAsset.newBuilder()
                .setId(m.getId().toString())
                .setUrl(nvl(m.getUrl()))
                .setIsThumbnail(m.getIsThumbnail())
                .setSortOrder(m.getSortOrder() == null ? 0 : m.getSortOrder())
                .build();
    }


    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
