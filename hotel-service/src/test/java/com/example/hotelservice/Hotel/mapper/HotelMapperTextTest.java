package com.example.hotelservice.Hotel.mapper;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelMapperTextTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private HotelMapper mapper(){
        var m=org.mapstruct.factory.Mappers.getMapper(HotelMapper.class);m.setObjectMapper(json);
        m.roomTypeMapper=mock(com.example.hotelservice.Room.mapper.RoomTypeMapper.class);
        m.hotelPolicyRepository=mock(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class);
        m.questionRepository=mock(com.example.hotelservice.Question.repository.QuestionRepository.class);
        m.hotelAmenityCategoryRepository=mock(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class);
        m.hotelAmenityRepository=mock(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class);
        m.amenityCatalog=mock(com.example.hotelservice.Amenity.service.AmenityCatalogService.class);return m;
    }
    @Test void generatedResponseMappingsNeverApplyJsonColumnEncoderToScalarText() throws Exception {
        var m=mapper();
        for(String text:java.util.List.of("\"Quoted hotel\"","null","undefined","Tiếng Việt 🌊\nDòng hai")){
            var city=new com.example.hotelservice.City.entity.City();city.setName(text);
            var hotel=Hotel.builder().id(java.util.UUID.randomUUID()).name(text).description(text).contactEmail("synthetic@example.invalid").contactPhone("+84000000000").city(city).address("{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\"}").build();
            for(var dto:java.util.List.of(m.toHotelResponse(hotel),m.toPublicHotelResponse(hotel))){
                assertThat(dto.name()).isEqualTo(text);assertThat(dto.description()).isEqualTo(text);assertThat(dto.city().name()).isEqualTo(text);
                assertThat(dto.contactEmail()).isEqualTo("synthetic@example.invalid");assertThat(dto.contactPhone()).isEqualTo("+84000000000");
                assertThat(json.valueToTree(dto).path("textEncoding").asText()).isEqualTo("PLAIN_V1");
            }
            hotel.setDescription(null);assertThat(m.toHotelResponse(hotel).description()).isNull();
        }
    }
    @Test void generatedCreateMappingKeepsTextAndEncodesOnlyTheJsonColumn() throws Exception {
        var body=json.createObjectNode().put("name","\"Quoted hotel\"").put("description","null").put("cityId",java.util.UUID.randomUUID().toString())
            .put("businessLicenseNumber","SYNTHETIC-LICENSE").put("taxId","SYNTHETIC-TAX").put("ownerIdentityNumber","SYNTHETIC-OWNER");
        body.set("address",json.createObjectNode().put("street","\"Quoted street\"").put("city","Synthetic city").put("country","VN"));
        var request=json.treeToValue(body,HotelCreateRequest.class);var entity=mapper().toHotel(request);
        assertThat(entity.getName()).isEqualTo(request.name());assertThat(entity.getDescription()).isEqualTo("null");assertThat(entity.getBusinessLicenseNumber()).isEqualTo("SYNTHETIC-LICENSE");
        var address=json.readTree(entity.getAddress());assertThat(address.isObject()).isTrue();assertThat(address.path("street").asText()).isEqualTo("\"Quoted street\"");
    }
}
