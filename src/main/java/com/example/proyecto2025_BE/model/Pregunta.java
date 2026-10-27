package com.example.proyecto2025_BE.model;

import com.example.proyecto2025_BE.utils.ContentNormalizer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "questions")
public class Pregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    @JsonIgnore
    private Topic topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado;

    @Column(name = "enunciado_hash", nullable = false, columnDefinition = "CHAR(64)")
    @JsonIgnore
    private String enunciadoHash;

    @Column(columnDefinition = "TEXT")
    private String explicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("positionOrder ASC")
    private List<Option> options = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (enunciadoHash == null) {
            enunciadoHash = ContentNormalizer.enunciadoHash(enunciado);
        }
    }

    @PreUpdate
    void onUpdate() {
        enunciadoHash = ContentNormalizer.enunciadoHash(enunciado);
    }

    @JsonIgnore
    public Option getCorrectOption() {
        return options.stream().filter(Option::isCorrect).findFirst().orElse(null);
    }

    public String getTopico() {
        return topic == null ? null : topic.getName();
    }

    public String getEmoji() {
        return topic == null ? null : topic.getEmoji();
    }

    public Long getUserId() {
        return topic == null ? null : topic.getUserId();
    }

    public BigDecimal difficultyFactor() {
        return difficulty.getDifficultyFactor();
    }

    public boolean isSuccess(LetterOption optionSelected) {
        Option correct = getCorrectOption();
        return correct != null && correct.getLetter().equals(optionSelected);
    }
}
