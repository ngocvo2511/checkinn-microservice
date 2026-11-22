package com.example.userservice.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** STD-026 (Batch 9F): a DB outage was a 401 with the raw message; now 503. */
class DatabaseOutageMappingTest {
    @RestController
    static class Outage {
        @GetMapping("/probe/no-connection")
        String noConnection() { throw new CannotCreateTransactionException("Could not open JPA EntityManager: jdbc:postgresql://secret-host"); }
    }

    @Test void poolWithoutConnectionIsA503DependencyFailureWithoutTheExceptionText() throws Exception {
        MockMvcBuilders.standaloneSetup(new Outage()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/probe/no-connection"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("secret-host"))));
    }
}
