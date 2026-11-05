package com.example.hotelservice.Room.mapper;

import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoomDescriptionTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void unicodeQuotedMarkupAndLongDescriptionsSurviveTheActualMapper() throws Exception {
        var mapper = org.mapstruct.factory.Mappers.getMapper(RoomTypeMapper.class);
        mapper.objectMapper = json;
        for (String description : java.util.Arrays.asList(null, "", "Phòng hướng biển 🌊\nTầng 2", "\"quoted text\"", "<script>plain text</script>", "Mô tả ".repeat(1000))) {
            var body = json.createObjectNode().put("hotelId", java.util.UUID.randomUUID().toString())
                    .put("name", "Synthetic room").put("basePrice", 1000000).put("roomAmount", 2);
            body.set("capacity", json.createObjectNode().put("adults", 2).put("children", 0));
            body.set("amenities", json.createArrayNode());
            body.put("description", description);
            var request = json.treeToValue(body, RoomTypeCreateRequest.class);
            var entity = mapper.toRoomType(request);
            var response = json.valueToTree(mapper.toRoomTypeResponse(entity));
            if (description == null) assertThat(response.path("description").isNull()).isTrue();
            else assertThat(response.path("description").asText()).isEqualTo(description);
        }
    }
}
