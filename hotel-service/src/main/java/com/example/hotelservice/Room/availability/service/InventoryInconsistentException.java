package com.example.hotelservice.Room.availability.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InventoryInconsistentException extends IllegalStateException {
    public InventoryInconsistentException() {
        super("Stored inventory counters or hold quantity are inconsistent and require review");
    }
}
