package com.example.hotelservice.security;

import com.example.hotelservice.grpc.*;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.example.hotelservice.MediaAsset.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;

class HotelGrpcPublicationAuthorizationTest {
    @ParameterizedTest @ValueSource(strings = {"hotel", "rooms", "room"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void hiddenCatalogUsesNotFoundWithoutMediaLookup(String method) {
        var hotels = mock(HotelService.class); var rooms = mock(RoomTypeService.class); var media = mock(MediaService.class);
        var server = new HotelGrpcServiceImpl(hotels, rooms, media, new ObjectMapper());
        var observer = mock(StreamObserver.class); UUID id = UUID.randomUUID();
        when(hotels.getPublishedById(id)).thenThrow(new jakarta.persistence.EntityNotFoundException());
        when(rooms.getPublicById(id)).thenThrow(new jakarta.persistence.EntityNotFoundException());
        when(rooms.getPublicByHotel(id)).thenThrow(new jakarta.persistence.EntityNotFoundException());
        if (method.equals("hotel")) server.getHotelById(GetHotelByIdRequest.newBuilder().setHotelId(id.toString()).build(), observer);
        if (method.equals("rooms")) server.getRoomTypesByHotel(GetRoomTypesByHotelRequest.newBuilder().setHotelId(id.toString()).build(), observer);
        if (method.equals("room")) server.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(), observer);
        verify(observer).onError(argThat(error -> Status.fromThrowable((Throwable) error).getCode() == Status.Code.NOT_FOUND));
        verify(observer, never()).onNext(any()); verifyNoInteractions(media);
    }
}
