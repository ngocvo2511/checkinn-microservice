package com.example.travelsocialservice.web.dto;

import com.example.travelsocialservice.web.dto.PostDtos.HotelView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvitationDtos {

    private InvitationDtos() {
    }

    /** {@code id} is the owner's own booking id, which they already see in their booking history. */
    public record InvitationView(UUID id, HotelView hotel, String checkOutMonth, Instant createdAt, Instant expiresAt) {
    }

    public record InvitationList(List<InvitationView> items) {
    }
}
