package com.example.hotelservice.Room.mapper;

import com.example.hotelservice.Room.entity.RoomType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class RoomAmenitiesCompatibilityTest {
    private final ObjectMapper json = new ObjectMapper();

    private RoomTypeMapper mapper() {
        var mapper = org.mapstruct.factory.Mappers.getMapper(RoomTypeMapper.class);
        mapper.objectMapper = json;
        return mapper;
    }

    private RoomType room(String amenities) {
        return RoomType.builder().capacity("{\"adults\":2,\"children\":0}")
                .amenities(amenities).description("\"null\"\nTiếng Việt 🌊").build();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "null")
    void optionalSqlAndJsonNullStayUnknownWithoutMutatingStoredValue(String stored) {
        var entity = room(stored);
        assertThat(mapper().toRoomTypeResponse(entity).amenities()).isNull();
        assertThat(entity.getAmenities()).isEqualTo(stored);
    }

    @Test
    void stringArrayRoundTripsLiteralTextAndPreservesEntity() throws Exception {
        var expected = java.util.List.of("null", "\"quoted\"", "Tiếng Việt 🌊\nDòng hai", "{\"a\":1}");
        var stored = json.writeValueAsString(expected);
        var entity = room(stored);
        var dto = mapper().toRoomTypeResponse(entity);
        assertThat(dto.amenities()).containsExactlyElementsOf(expected);
        assertThat(json.readTree(json.writeValueAsString(dto)).get("amenities"))
                .isEqualTo(json.readTree(stored));
        assertThat(dto.description()).isEqualTo(entity.getDescription());
        assertThat(entity.getAmenities()).isEqualTo(stored);
    }

    @Test
    void emptyArrayRemainsKnownEmpty() {
        assertThat(mapper().toRoomTypeResponse(room("[]")).amenities()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[7]", "[true]", "[null]", "[{\"title\":\"unknown\"}]",
            "\"[\\\"legacy\\\"]\"", "plain text", ""})
    void unsupportedOrDoubleEncodedShapesFailWithoutInventingValidLabels(String stored) {
        var entity = room(stored);
        assertThatThrownBy(() -> mapper().toRoomTypeResponse(entity)).isInstanceOf(IllegalStateException.class);
        assertThat(entity.getAmenities()).isEqualTo(stored);
    }
}
