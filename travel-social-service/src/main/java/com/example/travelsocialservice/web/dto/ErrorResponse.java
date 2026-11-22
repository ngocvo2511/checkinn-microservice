package com.example.travelsocialservice.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, Map<String, String> fields) {
}
