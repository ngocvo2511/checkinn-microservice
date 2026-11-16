package com.example.bookingservice.contract;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.controller.PaymentController;
import com.example.bookingservice.payment.service.PaymentService;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import com.example.bookingservice.grpc.interceptor.GrpcAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UuidContractTest {
    @ParameterizedTest @ValueSource(strings={"d3616870-e674-310a-b1ee-6a282806ec94", "11111111-1111-4111-8111-111111111111", "D3616870-E674-310A-B1EE-6A282806EC94"})
    void acceptsV3V4AndUppercaseWithoutRegeneration(String value) {
        assertThat(UuidContract.required(value).toString()).isEqualTo(value.toLowerCase(java.util.Locale.ROOT));
    }
    @ParameterizedTest @ValueSource(strings={"", "bad", "1-1-1-1-1", " 11111111-1111-4111-8111-111111111111"})
    void rejectsMalformedRequiredId(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> UuidContract.required(value));
    }
    @Test void keepsNullOptionalAndJsonStringRepresentation() throws Exception {
        assertThat(UuidContract.optional(null)).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> UuidContract.required(null));
        UUID id=UuidContract.required("11111111-1111-4111-8111-111111111111");
        var mapper=new ObjectMapper();
        assertThat(mapper.valueToTree(Payment.builder().id(id).bookingId(id).transactionId("receipt:NOT-UUID").build()).get("id").asText()).isEqualTo(id.toString());
        assertThat(mapper.readValue("{\"bookingId\":\""+id.toString().toUpperCase()+"\"}",CreatePaymentRequest.class).getBookingId()).isEqualTo(id);
        assertThatThrownBy(() -> mapper.readValue("{\"bookingId\":\"1-1-1-1-1\"}",CreatePaymentRequest.class)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThat(mapper.valueToTree(Payment.builder().transactionId("receipt:NOT-UUID").build()).get("transactionId").asText()).isEqualTo("receipt:NOT-UUID");
    }
    @Test void malformedRestIdStopsBeforeServiceAndGrpcErrorIsInvalidArgument() throws Exception {
        var service=mock(PaymentService.class);
        var conversions=new DefaultFormattingConversionService();
        new UuidWebConfiguration().addFormatters(conversions);
        var mvc=MockMvcBuilders.standaloneSetup(new PaymentController(service,new VnPayProperties(),mock(PaymentRepository.class))).setConversionService(conversions).setControllerAdvice(new UuidBindingAdvice()).build();
        mvc.perform(get("/api/payments/1-1-1-1-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        assertThat(io.grpc.Status.fromThrowable(GrpcAccess.error(new IllegalArgumentException("UUID"))).getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }
}
