package com.example.proyecto2025_BE.model.room;

import com.example.proyecto2025_BE.model.LetterOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta de una sala. Vive fuera de {@code answer} para no afectar score,
 * racha, ranking ni estadísticas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_answers_question_user",
                columnNames = {"question_id", "user_id"}))
public class RoomAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_selected", nullable = false)
    private LetterOption optionSelected;

    @Column(name = "milliseconds_spent", nullable = false)
    private long millisecondsSpent;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int points;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;
}
