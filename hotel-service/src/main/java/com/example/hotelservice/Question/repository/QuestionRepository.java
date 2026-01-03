package com.example.hotelservice.Question.repository;

import com.example.hotelservice.Question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
	List<Question> findAllByHotelId(UUID hotelId);
	void deleteAllByHotelId(UUID hotelId);
	Optional<Question> findByIdAndHotelId(UUID id, UUID hotelId);
}
