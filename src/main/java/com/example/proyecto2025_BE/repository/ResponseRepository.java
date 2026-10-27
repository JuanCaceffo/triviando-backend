package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ResponseRepository extends JpaRepository<Answer, Long> {

    Integer countByUserId(Long userId);

    Integer countByUserIdAndErrorReasonIsNull(Long userId);

    Answer findByUserIdAndQuestionId(Long userId, Long questionId);

    @Query("SELECT COALESCE(SUM(a.score), 0) FROM Answer a WHERE a.user.id = :userId")
    BigDecimal sumScoreByUserId(@Param("userId") Long userId);
}
