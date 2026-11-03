package com.example.proyecto2025_BE.model.room;

import com.example.proyecto2025_BE.model.LetterOption;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Opción de una pregunta de sala. La correcta nunca se expone antes del reveal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room_question_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_question_options_letter",
                columnNames = {"question_id", "letter"}))
public class RoomOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private RoomQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LetterOption letter;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "position_order", nullable = false)
    private int positionOrder;

    @JsonIgnore
    public boolean isCorrect() {
        return correct;
    }
}
