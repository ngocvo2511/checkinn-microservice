package com.example.hotelservice.validation;

import com.example.hotelservice.Hotel.controller.HotelController;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Policy.controller.PolicyController;
import com.example.hotelservice.Policy.service.PolicyCategoryService;
import com.example.hotelservice.Question.controller.QuestionController;
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

class HotelContentValidationTest {
 final HotelService hotels=mock(HotelService.class);MockMvc mvc;
 static final String ID="11111111-1111-4111-8111-111111111111";
 @BeforeEach void setup(){
  var mapper=mock(HotelMapper.class);
  mvc=MockMvcBuilders.standaloneSetup(new HotelController(hotels,mapper),new QuestionController(hotels,mapper),new PolicyController(hotels,mapper,mock(PolicyCategoryService.class)))
   .setControllerAdvice(new GlobalExceptionHandler(),new com.example.hotelservice.Hotel.controller.HotelContentFailureHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){
    public boolean supportsParameter(MethodParameter p){return p.getParameterType()==Jwt.class;}
    public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory f){return Jwt.withTokenValue("synthetic").header("alg","none").subject(ID).build();}
   }).build();
 }
 @ParameterizedTest @ValueSource(strings={"questions","policies"})
 void singleContentWriteActivatesDtoValidation(String kind) throws Exception {
  mvc.perform(post("/hotels/"+ID+"/"+kind).contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
  verifyNoInteractions(hotels);
 }
 @ParameterizedTest @ValueSource(strings={"\"questions\":[null]","\"questions\":[{\"question\":\"Synthetic question\",\"answer\":\" \"}]","\"policies\":[{\"category\":\"Synthetic\",\"content\":null}]"})
 void hotelCreationCascadesNestedContentValidation(String nested) throws Exception {
  String body="{\"name\":\"Synthetic hotel\",\"cityId\":\""+ID+"\",\"address\":{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\"},\"businessLicenseNumber\":\"synthetic\",\"taxId\":\"synthetic\",\"ownerIdentityNumber\":\"synthetic\",\"accommodationType\":\"HOTEL\","+nested+"}";
  mvc.perform(post("/hotels").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
  verifyNoInteractions(hotels);
 }
 @Test void bulkStorageFailureHasSafeRetryableStatus() throws Exception {
  when(hotels.updateQuestions(any(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-private-content-error"));
  mvc.perform(put("/hotels/"+ID+"/questions").contentType("application/json").content("[]"))
   .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private"))));
 }
}
