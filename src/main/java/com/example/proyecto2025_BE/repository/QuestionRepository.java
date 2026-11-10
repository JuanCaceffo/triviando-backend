package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.Pregunta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Pregunta, Long> {

    List<Pregunta> findByTopic_Id(Long topicId);

    Optional<Pregunta> findFirstByTopic_NameNormalizedAndTopic_UserId(String nameNormalized, Long userId);

    @Query("""
            SELECT p
            FROM Pregunta p
            WHERE p.topic.userId = :userId
              AND NOT EXISTS (
                  SELECT a.id
                  FROM Answer a
                  WHERE a.user.id = :userId AND a.questionId = p.id
              )
            """)
    List<Pregunta> findNotAnsweredByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT p
            FROM Pregunta p
            WHERE p.topic.userId = :userId
              AND p.topic.id = :topicId
              AND NOT EXISTS (
                  SELECT a.id
                  FROM Answer a
                  WHERE a.user.id = :userId AND a.questionId = p.id
              )
            """)
    List<Pregunta> findNotAnsweredByTopicIdAndUserId(
            @Param("topicId") Long topicId,
            @Param("userId") Long userId);

    /**
     * Cantidad de preguntas no respondidas por tópico del usuario.
     * Parte del tópico (LEFT JOIN) para que los tópicos sin preguntas o con
     * todas las preguntas respondidas aparezcan con cantidad 0.
     * Orden: más reciente primero; el id desempata para que el orden sea estable.
     */
    @Query("""
            SELECT t.name AS topic, t.emoji AS emoji, COUNT(p) AS cantidad
            FROM Topic t
            LEFT JOIN Pregunta p ON p.topic = t
                AND NOT EXISTS (
                    SELECT a.id
                    FROM Answer a
                    WHERE a.user.id = :userId AND a.questionId = p.id
                )
            WHERE t.userId = :userId
            GROUP BY t.id, t.name, t.emoji, t.createdAt
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<TopicCount> countUnansweredByTopic(@Param("userId") Long userId);

    interface TopicCount {
        String getTopic();

        String getEmoji();

        long getCantidad();
    }
}
