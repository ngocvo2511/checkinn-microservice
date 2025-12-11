package com.example.userservice.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String gender;
    private String birthday;
    private String country;
    private String address;
}
