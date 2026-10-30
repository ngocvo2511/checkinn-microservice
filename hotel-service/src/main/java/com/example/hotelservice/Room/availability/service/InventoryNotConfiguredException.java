package com.example.hotelservice.Room.availability.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InventoryNotConfiguredException extends IllegalStateException {
    public InventoryNotConfiguredException() {
        super("Room inventory is not configured with a positive totalRooms value");
    }
}
