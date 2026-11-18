package com.example.placeservice.dto.response;

import java.util.List;

public record PlaceSearchPage(List<PlaceSummary> items, String nextCursor) {
}
