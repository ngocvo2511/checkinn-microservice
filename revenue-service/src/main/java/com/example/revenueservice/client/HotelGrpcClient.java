package com.example.revenueservice.client;

import com.example.revenueservice.grpc.GetHotelByIdRequest;
import com.example.revenueservice.grpc.GetHotelByIdResponse;
import com.example.revenueservice.grpc.HotelGrpcServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HotelGrpcClient {
    @GrpcClient("hotel-service")
    private HotelGrpcServiceGrpc.HotelGrpcServiceBlockingStub stub;

    public HotelOwnerInfo getHotelInfo(String hotelId) {
        if (hotelId == null) {
            return new HotelOwnerInfo(null, null, null);
        }
        return fetchHotelInfo(hotelId);
    }

    public GetHotelByIdResponse getHotelById(String hotelId) {
        GetHotelByIdRequest request = GetHotelByIdRequest.newBuilder()
                .setHotelId(hotelId)
                .build();
        return stub.getHotelById(request);
    }

    private HotelOwnerInfo fetchHotelInfo(String hotelId) {
        try {
            GetHotelByIdRequest request = GetHotelByIdRequest.newBuilder()
                    .setHotelId(hotelId)
                    .build();
            GetHotelByIdResponse response = stub.getHotelById(request);
            String city = response.hasAddress() ? response.getAddress().getCity() : null;
            return new HotelOwnerInfo(response.getName(), response.getOwnerId(), city);
        } catch (Exception e) {
            // Log error but don't throw - return null to allow processing to continue
            System.err.println("Error fetching hotel info for " + hotelId + ": " + e.getMessage());
            return new HotelOwnerInfo(null, null, null);
        }
    }

    public record HotelOwnerInfo(String hotelName, String ownerId, String city) {}
}
