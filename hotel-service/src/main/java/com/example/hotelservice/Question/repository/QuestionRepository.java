package com.example.hotelservice.Question.repository;

import com.example.hotelservice.Question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
	List<Question> findAllByHotelId(UUID hotelId);
	/** Request-scoped bulk read for public representations; callers bound the ID chunk size. */
	@org.springframework.data.jpa.repository.Query("SELECT q FROM Question q WHERE q.hotel.id IN :hotelIds")
	List<Question> findAllByHotelIdIn(@org.springframework.data.repository.query.Param("hotelIds") java.util.Collection<UUID> hotelIds);
	void deleteAllByHotelId(UUID hotelId);
	Optional<Question> findByIdAndHotelId(UUID id, UUID hotelId);
}
