package com.example.placeservice.dto.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OsrmTableResponse {
    private String code;
    private List<List<Double>> distances;
    private List<List<Double>> durations;
    private List<Waypoint> sources;
    private List<Waypoint> destinations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Waypoint {
        private String hint;
        private Double distance;
        private String name;
        private List<Double> location;
    }
}
