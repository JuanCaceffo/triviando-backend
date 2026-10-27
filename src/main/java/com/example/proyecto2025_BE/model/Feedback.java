package com.example.proyecto2025_BE.model;

import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackOption feedbackType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum FeedbackOption {
        POSITIVE,
        NEGATIVE
    }

    public static Feedback fromDTO(FeedbackDTO feedbackDTO) {
        return Feedback.builder()
                .userId(feedbackDTO.userId())
                .questionId(feedbackDTO.questionId())
                .feedbackType(feedbackDTO.feedbackType())
                .description(feedbackDTO.description())
                .build();
    }

    public static FeedbackDTO toDTO(Feedback feedback) {
        return FeedbackDTO.builder()
                .userId(feedback.userId)
                .questionId(feedback.questionId)
                .feedbackType(feedback.feedbackType)
                .description(feedback.description)
                .build();
    }
}
