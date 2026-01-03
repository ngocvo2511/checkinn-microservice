package com.example.hotelservice.Question.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionRequest {
    String question;
    String answer;
}
