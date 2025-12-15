package com.example.userservice.model;

import com.example.userservice.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fullName;     // bắt buộc khi tạo account

    private String gender;       // optional
    private LocalDate birthDate; // optional
    private String phone;        // optional
    private String address;      // optional
    private String country;      // optional

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
