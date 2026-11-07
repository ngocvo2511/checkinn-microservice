package com.example.bookingservice.validation;

import com.example.bookingservice.booking.controller.BookingController;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.booking.service.CancellationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingValidationControllerTest {
    @ParameterizedTest @ValueSource(strings={"{}","{\"items\":[null]}","{\"adults\":1.5}","{\"hotelId\":\"not-a-uuid\",\"contactEmail\":\"bad-email\"}","{\"items\":[{\"quantity\":0}]}"})
    void malformedOrInvalidRequestIs400WithoutBusinessEffects(String body) throws Exception {
        var service=mock(BookingService.class);
        var json=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json();
        new com.example.bookingservice.config.ApiJsonConfig().strictIntegerInput().customize(json);
        var mvc=MockMvcBuilders.standaloneSetup(new BookingController(service,mock(CancellationPolicy.class)))
            .setControllerAdvice(new ValidationAdvice()).setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json.build())).build();
        mvc.perform(post("/api/bookings").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
        verifyNoInteractions(service);
    }
    @Test void directValidationIncludesNestedPathsWithoutEchoingContactValues() {
        var request=com.example.bookingservice.booking.dto.CreateBookingRequest.builder().hotelId("bad-id").contactEmail("private-malformed-input").build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> InputValidation.require(request))
            .isInstanceOfSatisfying(InputValidation.InvalidInput.class,e -> {
                org.assertj.core.api.Assertions.assertThat(e.getFieldErrors()).containsKeys("contactEmail","hotelId","items");
                org.assertj.core.api.Assertions.assertThat(e.getMessage()).doesNotContain("private-malformed-input");
            });
    }
}
