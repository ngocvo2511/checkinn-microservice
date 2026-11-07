package com.example.bookingservice.validation;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class ApiErrorContractTest {
    @RestController static class Fixture {
        @GetMapping("/fixture/internal") public Object internal(){throw new RuntimeException("synthetic internal detail");}
        @GetMapping("/fixture/legacy") public ResponseEntity<String> legacy(){return ResponseEntity.status(409).body("Inventory conflict");}
        @GetMapping("/fixture/empty") public ResponseEntity<Void> empty(){return ResponseEntity.badRequest().build();}
    }
    @Test void unexpectedErrorIsSanitizedAndHasStableCodeAndRequestId() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new ValidationAdvice(),new com.example.bookingservice.api.ApiErrorBodyAdvice()).build();
        mvc.perform(get("/fixture/internal")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.requestId",not(emptyOrNullString())))
                .andExpect(jsonPath("$.type").doesNotExist()).andExpect(content().string(not(containsString("synthetic internal detail"))));
    }
    @Test void legacyStringAndEmptyErrorResponsesHaveTheSameJsonEnvelope() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new ValidationAdvice(),new com.example.bookingservice.api.ApiErrorBodyAdvice()).build();
        mvc.perform(get("/fixture/legacy")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT")).andExpect(jsonPath("$.message").value("Inventory conflict"));
        mvc.perform(get("/fixture/empty")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unsupportedMethodIs405RatherThanInternalError() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new ValidationAdvice(),new com.example.bookingservice.api.ApiErrorBodyAdvice()).build();
        mvc.perform(post("/fixture/internal")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow",containsString("GET"))).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
