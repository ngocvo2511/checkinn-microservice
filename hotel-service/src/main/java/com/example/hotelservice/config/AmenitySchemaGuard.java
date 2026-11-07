package com.example.hotelservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Raw amenities remain compatible, but catalog tables must precede the new reader. */
@Component
@RequiredArgsConstructor
public class AmenitySchemaGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments args) {
        try {
            jdbc.execute("""
                SELECT c.code,c.label,c.description,c.active,c.revision,c.created_by,c.updated_by,c.created_at,c.updated_at,
                    s.amenity_id,s.hotel_id,s.revision,s.latest_request_id,s.approved_request_id,
                    r.id,r.hotel_id,r.amenity_id,r.title_snapshot,r.requested_by,r.requested_at,r.note,
                    r.status,r.reviewed_by,r.reviewed_at,r.review_reason,rc.request_id,rc.code
                FROM amenity_catalog c,amenity_mapping_states s,amenity_mapping_requests r,amenity_mapping_request_codes rc
                WHERE false
                """);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new IllegalStateException("Apply docker/sql/amenity-catalog-v1-20261106.sql before starting hotel-service");
        }
    }
}
