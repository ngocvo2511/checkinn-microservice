package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Room.availability.controller.AvailabilityController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.hotelservice.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvailabilitySnapshotTest {
    @Test void booleanAndCountMustDescribeOneObservationEvenIfStockChangesBetweenReads() throws Exception {
        var service=mock(AvailabilityService.class);
        UUID room=UUID.randomUUID(); var in=LocalDate.of(2027,1,1);var out=in.plusDays(1);
        // Old controller did an independent positive check, then a later zero count.
        when(service.isAvailable(room,in,out,1)).thenReturn(true);
        when(service.getAvailableRoomCount(room,in,out)).thenReturn(0);
        var mvc=MockMvcBuilders.standaloneSetup(new AvailabilityController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/availability/check").param("roomTypeId",room.toString()).param("checkIn",in.toString()).param("checkOut",out.toString()).param("quantity","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableRooms").value(0)).andExpect(jsonPath("$.available").value(false));
        verify(service,times(1)).getAvailableRoomCount(room,in,out);
        verify(service,never()).isAvailable(any(),any(),any(),anyInt());
    }
    @Test void invalidQuantityIsRejectedBeforeReadingStock() throws Exception {
        var service=mock(AvailabilityService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AvailabilityController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/availability/check").param("roomTypeId",UUID.randomUUID().toString()).param("checkIn","2027-01-01").param("checkOut","2027-01-02").param("quantity","0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
