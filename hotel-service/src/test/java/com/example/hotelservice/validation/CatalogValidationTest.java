package com.example.hotelservice.validation;

import com.example.hotelservice.Hotel.controller.HotelController;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Room.controller.RoomTypeController;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.example.hotelservice.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogValidationTest {
    final HotelService hotels=mock(HotelService.class);
    final RoomTypeService rooms=mock(RoomTypeService.class);
    MockMvc mvc;
    static final String ID="11111111-1111-4111-8111-111111111111";
    @BeforeEach void setup() {
        var json=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json();
        new com.example.hotelservice.config.ApiJsonConfig().strictIntegerInput().customize(json);
        mvc=MockMvcBuilders.standaloneSetup(new HotelController(hotels,mock(HotelMapper.class)),new RoomTypeController(rooms,mock(RoomTypeMapper.class)))
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json.build())).setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter p) { return p.getParameterType()==Jwt.class; }
                public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory f) {
                    return Jwt.withTokenValue("synthetic").header("alg","none").subject(ID).claim("role","OWNER").build();
                }
            }).build();
    }
    @ParameterizedTest @ValueSource(strings={"{\"name\":\" \"}","{\"starRating\":6}","{\"address\":{\"street\":\"\",\"city\":\"City\",\"country\":\"VN\"}}","{\"contactEmail\":\"bad-address\"}"})
    void hotelUpdateRejectsInvalidSuppliedValues(String body) throws Exception {
        mvc.perform(put("/hotels/"+ID).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
        verifyNoInteractions(hotels,rooms);
    }
    @Test void hotelCreateActivatesExistingRequiredAndNestedConstraints() throws Exception {
        mvc.perform(post("/hotels").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
        verifyNoInteractions(hotels,rooms);
    }
    @ParameterizedTest @ValueSource(strings={"{\"basePrice\":-1}","{\"roomAmount\":0}","{\"capacity\":{\"adults\":-1,\"children\":0}}","{\"capacity\":{\"adults\":1}}","{\"name\":\" \"}","{\"roomAmount\":1.5}"})
    void roomUpdateRejectsInvalidOrFractionalInput(String body) throws Exception {
        mvc.perform(put("/api/v1/"+ID).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(hotels,rooms);
    }
    @Test void partialUpdatesRemainValid() throws Exception {
        mvc.perform(put("/hotels/"+ID).contentType("application/json").content("{}" )).andExpect(status().isOk());
        mvc.perform(put("/api/v1/"+ID).contentType("application/json").content("{\"description\":\"\"}" )).andExpect(status().isOk());
        verify(hotels).updateHotel(any(),any(),any()); verify(rooms).updateRoomType(any(),any(),any());
    }
}
