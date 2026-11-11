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
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
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
            UUID hotelId = catalogId(request.getHotelId());

            Hotel hotel = hotelService.getPublishedById(hotelId);
            List<RoomType> roomTypes = roomTypeService.getPublicByHotel(hotelId);
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

        } catch (Exception failure) {
            fail(failure,responseObserver);
        }
    }

    // ============================================================
    // GET ROOM TYPES BY HOTEL
    // ============================================================
    @Override
    public void getRoomTypesByHotel(GetRoomTypesByHotelRequest request,
                                    StreamObserver<GetRoomTypesByHotelResponse> responseObserver) {

        try {
            UUID hotelId = catalogId(request.getHotelId());
            List<RoomType> roomTypes = roomTypeService.getPublicByHotel(hotelId);

            GetRoomTypesByHotelResponse.Builder builder = GetRoomTypesByHotelResponse.newBuilder();

            var imagesByRoom = new java.util.HashMap<UUID,java.util.List<MediaAsset>>();
            if (!roomTypes.isEmpty()) {
                var roomIds = roomTypes.stream().map(RoomType::getId).collect(java.util.stream.Collectors.toSet());
                for (MediaAsset image : mediaAssetService.getPublicRoomMediaByHotel(hotelId)) {
                    if (image.getTargetType()!=MediaTargetType.ROOM_TYPE || !roomIds.contains(image.getTargetId())) throw new CatalogDataException();
                    imagesByRoom.computeIfAbsent(image.getTargetId(),key->new java.util.ArrayList<>()).add(image);
                }
            }
            for (RoomType rt : roomTypes) {
                if (!hotelId.equals(rt.getHotelId())) throw new CatalogDataException();
                builder.addRoomTypes(mapRoomTypeDto(rt, imagesByRoom.getOrDefault(rt.getId(),List.of())));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception failure) {
            fail(failure,responseObserver);
        }
    }

    // ============================================================
    // GET ROOM TYPE BY ID
    // ============================================================
    @Override
    public void getRoomTypeById(GetRoomTypeByIdRequest request,
                                StreamObserver<GetRoomTypeByIdResponse> responseObserver) {

        try {
            UUID rtId = catalogId(request.getRoomTypeId());
            RoomType roomType = roomTypeService.getPublicById(rtId);

            List<MediaAsset> images = mediaAssetService.getByTarget(rtId, MediaTargetType.ROOM_TYPE);

            RoomTypeDto dto = mapRoomTypeDto(roomType, images);

            GetRoomTypeByIdResponse response = GetRoomTypeByIdResponse.newBuilder()
                    .setRoomType(dto)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception failure) {
            fail(failure,responseObserver);
        }
    }

    // ============================================================
    // MAPPING HELPERS
    // ============================================================

    private HotelAddress mapHotelAddress(String json) throws Exception {
        var address = objectMapper.readValue(json, com.example.hotelservice.Hotel.dto.request.HotelAddressDto.class).forRead();
        return HotelAddress.newBuilder().setStreet(nvl(address.street())).setCity(nvl(address.city())).setCountry(nvl(address.country()))
                .setLatitude(address.latitude() == null ? 0 : address.latitude())
                .setLongitude(address.longitude() == null ? 0 : address.longitude())
                .setCoordinateSource(nvl(address.coordinateSource())).build();
    }

    private RoomTypeBasic mapRoomTypeBasic(RoomType rt) {
        if (rt.getTotalRooms()==null || rt.getTotalRooms()<=0) throw new com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException();
        return RoomTypeBasic.newBuilder()
                .setId(rt.getId().toString())
                .setName(nvl(rt.getName()))
                .setBasePrice(price(rt))
                .setTotal(rt.getTotalRooms())
                .build();
    }

    private RoomTypeDto mapRoomTypeDto(RoomType rt, List<MediaAsset> images) throws Exception {

        RoomTypeDto.Builder builder = RoomTypeDto.newBuilder()
                .setId(rt.getId().toString())
                .setHotelId(rt.getHotelId().toString())
                .setName(nvl(rt.getName()))
                .setBasePrice(price(rt))
                .setIsActive(rt.getIsActive());

        if (rt.getDescription() != null) builder.setDescription(rt.getDescription());

        // capacity
        if (rt.getCapacity() != null) {
            var capacity=mapCapacity(rt.getCapacity());
            if(capacity!=null)builder.setCapacity(capacity);
        }

        // amenities
        if (rt.getAmenities() != null) {
            JsonNode amenities = objectMapper.readTree(rt.getAmenities());
            if (amenities==null)throw new CatalogDataException();
            if (!amenities.isNull()) {
                if (!amenities.isArray()) throw new CatalogDataException();
                for(JsonNode item:amenities) {
                    if(!item.isTextual()) throw new CatalogDataException();
                    builder.addAmenities(item.textValue());
                }
            }
        }

        for (MediaAsset img : images) {
            builder.addImages(mapMedia(img));
        }

        return builder.build();
    }

    private Capacity mapCapacity(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        if(node==null)throw new CatalogDataException();
        if(node.isNull())return null;
        if(!node.isObject())throw new CatalogDataException();
        return Capacity.newBuilder().setAdults(count(node,"adults",1)).setChildren(count(node,"children",0)).build();
    }

    private int count(JsonNode node,String name,int minimum) {
        var value=node.get(name);
        if(value==null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<minimum)throw new CatalogDataException();
        return value.intValue();
    }

    private double price(RoomType room) {
        if(room.getBasePrice()==null || room.getBasePrice().signum()<=0)throw new CatalogDataException();
        double value=room.getBasePrice().doubleValue();
        if(!Double.isFinite(value) || value<=0)throw new CatalogDataException();
        return value;
    }

    private UUID catalogId(String raw) {
        UUID id=UUID.fromString(raw);
        if(!id.toString().equalsIgnoreCase(raw))throw new IllegalArgumentException("Invalid catalog identifier");
        return id;
    }

    private static class CatalogDataException extends RuntimeException {}

    private void fail(Exception failure,StreamObserver<?> observer) {
        Status.Code code;
        if(failure instanceof CatalogDataException || failure instanceof com.fasterxml.jackson.core.JsonProcessingException)code=Status.Code.DATA_LOSS;
        else if(failure instanceof com.example.hotelservice.Room.availability.service.InventoryNotConfiguredException)code=Status.Code.FAILED_PRECONDITION;
        else if(failure instanceof jakarta.persistence.EntityNotFoundException)code=Status.Code.NOT_FOUND;
        else if(failure instanceof org.springframework.dao.DataAccessException)code=Status.Code.UNAVAILABLE;
        else if(failure instanceof io.grpc.StatusRuntimeException rpc)code=rpc.getStatus().getCode();
        else if(failure instanceof org.springframework.web.server.ResponseStatusException http)code=switch(http.getStatusCode().value()) {
            case 400->Status.Code.INVALID_ARGUMENT;case 401->Status.Code.UNAUTHENTICATED;case 403->Status.Code.PERMISSION_DENIED;
            case 404->Status.Code.NOT_FOUND;case 503->Status.Code.UNAVAILABLE;case 504->Status.Code.DEADLINE_EXCEEDED;default->Status.Code.INTERNAL;
        };
        else if(failure instanceof SecurityException || failure instanceof org.springframework.security.access.AccessDeniedException)code=Status.Code.PERMISSION_DENIED;
        else if(failure instanceof IllegalArgumentException)code=Status.Code.INVALID_ARGUMENT;
        else code=Status.Code.INTERNAL;
        String message=switch(code) {
            case INVALID_ARGUMENT->"Invalid catalog identifier";case NOT_FOUND->"Published catalog item not found";
            case DATA_LOSS->"Stored catalog data is invalid";case FAILED_PRECONDITION->"Catalog inventory is not configured";
            case UNAVAILABLE->"Catalog storage temporarily unavailable";case DEADLINE_EXCEEDED->"Catalog dependency timed out";
            default->"Catalog request could not be completed";
        };
        log.warn("[CATALOG_RPC_FAILED] {}",code);
        observer.onError(Status.fromCode(code).withDescription(message).asRuntimeException());
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
