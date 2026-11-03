package com.example.proyecto2025_BE.model.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Participante de una sala. El puntaje es exclusivo de la partida y nunca se
 * traslada al score global del usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_members_room_user",
                columnNames = {"room_id", "user_id"}))
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @Column(nullable = false)
    private int score = 0;

    @Builder.Default
    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    @Builder.Default
    @Column(name = "total_response_ms", nullable = false)
    private long totalResponseMs = 0;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
