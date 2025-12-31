package com.example.hotelservice.Question.entity;

import com.example.hotelservice.Hotel.entity.Hotel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Column(name = "question", nullable = false, length = 255)
	private String question;

	@Column(name = "answer", columnDefinition = "TEXT", nullable = false)
	private String answer;

	@Builder.Default
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void prePersist() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}
}
