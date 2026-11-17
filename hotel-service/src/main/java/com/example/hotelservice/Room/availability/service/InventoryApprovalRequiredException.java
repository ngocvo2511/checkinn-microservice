package com.example.hotelservice.Room.availability.service;

/** Public business failure; never exposes private review evidence. */
public class InventoryApprovalRequiredException extends IllegalStateException {
    public InventoryApprovalRequiredException() {
        super("INVENTORY_APPROVAL_REQUIRED: Room inventory is not approved for new reservations");
    }
}
