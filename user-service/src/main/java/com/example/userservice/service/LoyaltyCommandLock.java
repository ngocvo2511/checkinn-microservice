package com.example.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

/** PostgreSQL transaction locks also serialize commands for accounts not created yet. */
@Component
@RequiredArgsConstructor
public class LoyaltyCommandLock {
    private final JdbcTemplate jdbc;

    public void acquire(UUID bookingId, UUID userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Loyalty command requires a database transaction");
        }
        // Every command takes booking before account; never invert this order.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var timeout = connection.prepareStatement("SELECT set_config('lock_timeout', '5s', true)")) {
                timeout.execute();
            }
            for (String key : new String[]{"loyalty:booking:" + bookingId, "loyalty:user:" + userId}) {
                try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    lock.setString(1, key);
                    lock.execute();
                }
            }
            return null;
        });
    }
}
