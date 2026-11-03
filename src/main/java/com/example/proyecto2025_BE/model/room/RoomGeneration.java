package com.example.proyecto2025_BE.model.room;

import com.example.proyecto2025_BE.model.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Trabajo asíncrono de generación de preguntas para una sala. Se mantiene
 * separado de {@code generation_jobs} para no tocar el flujo normal de temas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room_generations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_generations_room",
                columnNames = "room_id"))
public class RoomGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Builder.Default
    @Column(nullable = false)
    private int progress = 0;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public boolean isFinished() {
        return JobStatus.COMPLETED.equals(status) || JobStatus.FAILED.equals(status);
    }
}
