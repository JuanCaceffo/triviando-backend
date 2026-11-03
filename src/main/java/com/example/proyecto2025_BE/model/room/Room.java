package com.example.proyecto2025_BE.model.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sala efímera de trivia en vivo. No tiene relación con {@code topics} ni
 * {@code answer}: sus preguntas y respuestas viven en tablas propias para no
 * afectar rankings ni estadísticas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_code",
                columnNames = "code"))
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoomSource source;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(name = "question_count", nullable = false)
    @Builder.Default
    private int questionCount = 0;

    /**
     * Índice 0-based de la pregunta actual; -1 antes de iniciar la partida.
     */
    @Column(name = "current_question_index", nullable = false)
    @Builder.Default
    private int currentQuestionIndex = -1;

    @Column(name = "question_started_at")
    private LocalDateTime questionStartedAt;

    @Column(name = "question_deadline_at")
    private LocalDateTime questionDeadlineAt;

    @Column(name = "reveal_until")
    private LocalDateTime revealUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 30)
    private RoomCloseReason closeReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    private long version;
}
