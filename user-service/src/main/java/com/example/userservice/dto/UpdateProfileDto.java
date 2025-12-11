package com.example.userservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileDto {
    private String fullName;
    private String phone;
    private String gender;
    private String birthday;
    private String country;
    private String address;
}
