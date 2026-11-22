package com.example.placeservice.controller;

import com.example.placeservice.dto.response.PlaceSearchPage;
import com.example.placeservice.service.PlaceCatalogService;
import com.example.placeservice.service.PlaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The literal /search and /batch routes must win over /{id}. */
class PlaceControllerRoutingTest {

    private PlaceService placeService;
    private PlaceCatalogService catalogService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        catalogService = mock(PlaceCatalogService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlaceController(placeService, catalogService)).build();
    }

    @Test
    void searchRouteReachesCatalogService() throws Exception {
        when(catalogService.search("hoi an", null, 5)).thenReturn(new PlaceSearchPage(List.of(), null));

        mvc.perform(get("/places/search").param("q", "hoi an").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        verify(catalogService).search("hoi an", null, 5);
        verifyNoInteractions(placeService);
    }

    @Test
    void batchRouteParsesCommaSeparatedIds() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(catalogService.batch(anyList())).thenReturn(List.of());

        mvc.perform(get("/places/batch").param("ids", a + "," + b)).andExpect(status().isOk());

        verify(catalogService).batch(List.of(a, b));
        verifyNoInteractions(placeService);
    }
}
