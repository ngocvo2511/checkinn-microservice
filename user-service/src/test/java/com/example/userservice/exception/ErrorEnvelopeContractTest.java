package com.example.userservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** STD-026 (Batch 9H): user-service errors use {timestamp,status,error,code,message,path,requestId} + X-Request-ID. */
class ErrorEnvelopeContractTest {
    @RestController
    static class Probe {
        @GetMapping("/probe/business") String business() { throw new UserBusinessException("Email đã tồn tại"); }
        @GetMapping("/probe/points") String points() { throw new IllegalArgumentException("secret-host"); }
        @GetMapping("/probe/bug") String bug() { throw new RuntimeException("secret-host"); }
    }

    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();

    void envelope(String path, int status, String code, String message) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().is(status))
                .andExpect(jsonPath("$.timestamp").isNotEmpty()).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message)).andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        String header = result.getResponse().getHeader("X-Request-ID");
        assertThat(header).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + header + "\"").doesNotContain("secret-host");
    }

    @Test void errorsCarryTheStandardEnvelope() throws Exception {
        envelope("/probe/business", 401, "UNAUTHORIZED", "Email đã tồn tại");
        envelope("/probe/points", 400, "INVALID_REQUEST", "Invalid request or insufficient points");
        envelope("/probe/bug", 500, "INTERNAL_ERROR", "Internal server error");
    }
}
