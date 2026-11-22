package com.example.revenueservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** STD-026 (Batch 9H): revenue-service errors use {timestamp,status,error,code,message,path,requestId} + X-Request-ID. */
class ErrorEnvelopeContractTest {
    @RestController
    static class Probe {
        @GetMapping("/probe/invalid") String invalid() { throw new IllegalArgumentException("secret-host"); }
        @GetMapping("/probe/reconcile") String reconcile() { throw new ReportReconciliationRequiredException(); }
    }

    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ReportExceptionHandler()).build();

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
        envelope("/probe/invalid", 400, "BAD_REQUEST", "BAD_REQUEST");
        envelope("/probe/reconcile", 503, "REPORT_RECONCILIATION_REQUIRED", "Historical or conflicting cash transactions require reconciliation");
    }
}
