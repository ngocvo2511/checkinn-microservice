package com.example.placeservice.dto.osrm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadMetrics implements Serializable {
    private Double distanceKm;
    private Integer durationMinutes;
    private boolean fallback;
}
