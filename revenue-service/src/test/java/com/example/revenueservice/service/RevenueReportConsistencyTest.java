package com.example.revenueservice.service;
import static com.example.revenueservice.contract.TestIds.*;

import com.example.revenueservice.client.*;
import com.example.revenueservice.repository.*;
import com.example.revenueservice.entity.PaymentRecord;
import com.example.revenueservice.regulations.RegulationClient;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevenueReportConsistencyTest {
    @Test void multipleCashReceiptsDoNotCountOneBookingTwice() {
        var flows=mock(RevenueCashFlows.class);var bookings=mock(BookingStatusRecordRepository.class);
        var hotels=mock(HotelGrpcClient.class);var regulation=mock(RegulationClient.class);
        var rows=new ArrayList<PaymentRecord>();
        for(String booking:List.of("first","first","second")) {
            var row=new PaymentRecord();row.setBookingId(id(booking));row.setHotelId(id("hotel"));row.setCustomerId(id("guest"));
            row.setAmount(new BigDecimal("100"));row.setPaymentStatus("COMPLETED");row.setPaidAt(LocalDateTime.of(2027,1,1,12,0));rows.add(row);
        }
        when(flows.read(any(),any(),any())).thenReturn(rows);
        when(hotels.getHotelInfo(wire("hotel"))).thenReturn(new HotelGrpcClient.HotelOwnerInfo("Synthetic","owner","Synthetic city"));
        when(hotels.getHotelById(wire("hotel"))).thenReturn(com.example.revenueservice.grpc.GetHotelByIdResponse.newBuilder()
                .addRoomTypes(com.example.revenueservice.grpc.RoomTypeBasic.newBuilder().setTotal(5)).build());
        when(regulation.getCommissionRate()).thenReturn(new BigDecimal("0.23"));
        var service=new RevenueReportService(flows,bookings,mock(HotelCapacityClient.class),hotels,regulation);
        var result=service.adminSummary(LocalDate.of(2027,1,1),LocalDate.of(2027,1,2));
        assertThat(result.totalRevenue()).isEqualByComparingTo("300");assertThat(result.totalBookings()).isEqualTo(2);
        assertThat(result.topHotels().getFirst().bookingCount()).isEqualTo(2);
        verify(regulation,times(1)).getCommissionRate();
    }
    @Test void ownerSummaryCannotReportZeroOccupancyWhenCatalogIsUnavailable() {
        var payments=mock(RevenueCashFlows.class);var bookings=mock(BookingStatusRecordRepository.class);
        var hotels=mock(HotelGrpcClient.class);var regulation=mock(RegulationClient.class);
        var row=new PaymentRecord();row.setHotelId(id("synthetic-hotel"));row.setAmount(BigDecimal.TEN);row.setPaymentStatus("COMPLETED");
        when(payments.readOwner(anyString(),any(),any())).thenReturn(List.of(row));
        when(hotels.getHotelInfo(wire("synthetic-hotel"))).thenReturn(new HotelGrpcClient.HotelOwnerInfo("Synthetic","owner",null));
        when(hotels.getHotelById(wire("synthetic-hotel"))).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Synthetic outage"));
        when(regulation.getCommissionRate()).thenReturn(new BigDecimal("0.10"));
        var service=new RevenueReportService(payments,bookings,mock(HotelCapacityClient.class),hotels,regulation);
        assertThatThrownBy(()->service.ownerSummary("owner",LocalDate.of(2027,1,1),LocalDate.of(2027,1,2)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
