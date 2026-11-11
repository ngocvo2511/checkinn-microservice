package com.example.hotelservice.Question.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    String question;
    @jakarta.validation.constraints.NotBlank
    String answer;
}
