package com.example.bookingservice.messaging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class BookingEventDeliveryTest {
    @ParameterizedTest @ValueSource(strings={"ACK","NACK","RETURNED","TIMEOUT","TRANSPORT","INTERRUPTED"})
    void onlyRoutedPositiveConfirmationCanCompleteOutbox(String outcome,CapturedOutput output) {
        var jdbc=mock(JdbcTemplate.class);var rabbit=mock(RabbitTemplate.class);UUID id=UUID.randomUUID();
        String marker="synthetic-private-body-and-transport-marker";
        when(jdbc.queryForList(anyString(),eq(id))).thenReturn(List.of(Map.of("attempts",0,"exchange_name","hotel.events","routing_key","payment.completed","payload",marker)));
        doAnswer(call->{
            if(outcome.equals("TRANSPORT"))throw new IllegalStateException(marker);
            var data=call.getArgument(3,CorrelationData.class);
            if(outcome.equals("RETURNED"))data.setReturned(new ReturnedMessage(new Message(marker.getBytes(),new MessageProperties()),312,marker,"hotel.events","payment.completed"));
            if(!Set.of("TIMEOUT","INTERRUPTED").contains(outcome))data.getFuture().complete(new CorrelationData.Confirm(!outcome.equals("NACK"),marker));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        try {
            if(outcome.equals("INTERRUPTED"))Thread.currentThread().interrupt();
            new BookingEventDelivery(jdbc,rabbit,1,1).deliver(id);
            if(outcome.equals("ACK")) verify(jdbc).update(contains("state='DELIVERED'"),eq(1),eq(id));
            else {
                verify(jdbc,never()).update(contains("state='DELIVERED'"),any(),any());
                String code=switch(outcome) {case "NACK"->"BROKER_NACK";case "RETURNED"->"UNROUTABLE_EVENT";case "TIMEOUT"->"CONFIRM_TIMEOUT";case "INTERRUPTED"->"DELIVERY_INTERRUPTED";default->"BROKER_UNAVAILABLE";};
                verify(jdbc).update(contains("next_attempt_at="),eq(1),eq(1),eq(code),eq(id));
            }
            assertThat(output.getAll()).doesNotContain(marker);
        } finally { Thread.interrupted(); }
    }
}
