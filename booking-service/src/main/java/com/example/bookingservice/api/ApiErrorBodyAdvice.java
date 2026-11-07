package com.example.bookingservice.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Wrap legacy String/Map/empty errors while preserving their actual HTTP status. */
@RestControllerAdvice
public class ApiErrorBodyAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter parameter,Class<? extends HttpMessageConverter<?>> converter) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter)
            || StringHttpMessageConverter.class.isAssignableFrom(converter) || ByteArrayHttpMessageConverter.class.isAssignableFrom(converter);
    }
    @Override public Object beforeBodyWrite(Object value,MethodParameter parameter,MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(!(response instanceof ServletServerHttpResponse servlet)) return value;
        int status=servlet.getServletResponse().getStatus();
        if(status<400) return value;
        var body=ApiErrorSupport.body(status,request.getURI().getRawPath(),value);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        response.getHeaders().set("X-Request-ID",body.get("requestId").toString());
        response.getHeaders().setCacheControl("no-store");
        if(StringHttpMessageConverter.class.isAssignableFrom(converter)) return ApiErrorSupport.json(body);
        if(ByteArrayHttpMessageConverter.class.isAssignableFrom(converter)) return ApiErrorSupport.json(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return body;
    }
}
