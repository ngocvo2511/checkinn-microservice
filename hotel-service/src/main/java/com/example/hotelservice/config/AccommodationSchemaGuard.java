package com.example.hotelservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Refuse an incomplete ddl-auto expansion: deployment SQL owns checks and the partial unique index. */
@Component @RequiredArgsConstructor
public class AccommodationSchemaGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments args) {
        Boolean valid = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='hotels'::regclass
                AND conname='hotels_accommodation_type_allowed' AND convalidated)
            AND EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                WHERE c.relname='accommodation_change_one_pending' AND i.indisunique AND i.indisvalid)
            AND EXISTS (SELECT 1 FROM checkinn_feature_migrations WHERE migration_id='hotel-accommodation-type-20261104')
            """, Boolean.class);
        if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("Apply docker/sql/hotel-accommodation-type-20261104.sql before starting hotel-service");
    }
}
