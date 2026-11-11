package com.example.hotelservice.validation;

import com.example.hotelservice.Hotel.controller.*;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Amenity.controller.AmenityController;
import com.example.hotelservice.Amenity.service.AmenityCategoryService;
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

class AmenityWriteValidationTest {
 final HotelService hotels=mock(HotelService.class);MockMvc mvc;
 static final String ID="11111111-1111-4111-8111-111111111111";
 @BeforeEach void setup(){
  var mapper=mock(HotelMapper.class);
  mvc=MockMvcBuilders.standaloneSetup(new HotelController(hotels,mapper),new AmenityController(hotels,mapper,mock(AmenityCategoryService.class)))
   .setControllerAdvice(new GlobalExceptionHandler(),new HotelContentFailureHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){
    public boolean supportsParameter(MethodParameter p){return p.getParameterType()==Jwt.class;}
    public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory f){return Jwt.withTokenValue("synthetic").header("alg","none").subject(ID).build();}
   }).build();
 }
 @ParameterizedTest @ValueSource(strings={"{}","{\"amenityCategories\":null}","{\"amenityCategories\":[null]}","{\"amenityCategories\":[{\"title\":\"Synthetic\",\"amenities\":[null]}]}"})
 void replacementValidatesTheWholeNestedBody(String body) throws Exception {
  mvc.perform(put("/hotels/"+ID+"/amenities").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());verifyNoInteractions(hotels);
 }
 @ParameterizedTest @ValueSource(strings={"{\"title\":\"Synthetic\",\"items\":[{\"title\":\"Preserve me\"}]}","{\"title\":\"Synthetic\",\"items\":[],\"amenities\":[]}"})
 void responseShapeCannotSilentlyClearItems(String category) throws Exception {
  mvc.perform(put("/hotels/"+ID+"/amenities").contentType("application/json").content("{\"amenityCategories\":["+category+"]}"))
   .andExpect(status().isBadRequest());verifyNoInteractions(hotels);
 }
 @Test void singleCategoryValidatesBeforeService() throws Exception {
  mvc.perform(post("/hotels/"+ID+"/amenities").contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());verifyNoInteractions(hotels);
 }
 @Test void hotelCreationCascadesToAmenityItems() throws Exception {
  String body="{\"name\":\"Synthetic hotel\",\"cityId\":\""+ID+"\",\"address\":{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\"},\"businessLicenseNumber\":\"synthetic\",\"taxId\":\"synthetic\",\"ownerIdentityNumber\":\"synthetic\",\"accommodationType\":\"HOTEL\",\"amenityCategories\":[{\"title\":\"Synthetic\",\"amenities\":[{\"title\":\" \"}]}]}";
  mvc.perform(post("/hotels").contentType("application/json").content(body)).andExpect(status().isBadRequest());verifyNoInteractions(hotels);
 }
 @Test void embeddedHotelUpdateRejectsNullCategory() throws Exception {
  mvc.perform(put("/hotels/"+ID).contentType("application/json").content("{\"amenityCategories\":[null]}"))
   .andExpect(status().isBadRequest());verifyNoInteractions(hotels);
 }
 @Test void storageFailureIsSafe503() throws Exception {
  when(hotels.updateAmenities(any(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-private-storage"));
  mvc.perform(put("/hotels/"+ID+"/amenities").contentType("application/json").content("{\"amenityCategories\":[]}"))
   .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private"))));
 }
}
