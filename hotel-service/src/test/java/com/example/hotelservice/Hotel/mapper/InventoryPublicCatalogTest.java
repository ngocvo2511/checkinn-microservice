package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryPublicCatalogTest {
    @Test void publicHotelDetailExcludesUnapprovedInventoryWhileKeepingApprovedRooms() {
        var mapper=org.mapstruct.factory.Mappers.getMapper(HotelMapper.class);
        var rooms=mock(RoomTypeRepository.class);var roomMapper=mock(RoomTypeMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(mapper,"inventoryRooms",rooms);
        org.springframework.test.util.ReflectionTestUtils.setField(mapper,"roomTypeMapper",roomMapper);
        var approved=new RoomType();approved.setId(UUID.randomUUID());approved.setIsActive(true);approved.setBasePrice(java.math.BigDecimal.TEN);
        var pending=new RoomType();pending.setId(UUID.randomUUID());pending.setIsActive(true);pending.setTotalRooms(10);pending.setBasePrice(java.math.BigDecimal.ONE);
        var dto=mock(RoomTypeResponse.class);
        var approvedIds=Set.of(approved.getId());
        when(roomMapper.toRoomTypeResponse(approved)).thenReturn(dto);
        assertThat(mapper.toPublishedRoomTypeResponses(List.of(pending,approved),approvedIds)).containsExactly(dto);
        assertThat(mapper.calculateApprovedLowestPrice(List.of(pending,approved),approvedIds)).isEqualByComparingTo("10");
        assertThat(mapper.calculateApprovedLowestPrice(List.of(pending),approvedIds)).isNull();
        verify(roomMapper,never()).toRoomTypeResponse(pending);
        assertThat(mapper.toPublishedRoomTypeResponses(List.of(pending),approvedIds)).isEmpty();
    }
}
