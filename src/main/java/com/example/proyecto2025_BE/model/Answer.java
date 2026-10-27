package com.example.proyecto2025_BE.model;

import com.example.proyecto2025_BE.constants.UnsuccessReasons;
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

@Data
@Builder
@Entity
@Table(name = "answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_answer_user_question",
                columnNames = {"user_id", "question_id"}))
@AllArgsConstructor
@NoArgsConstructor
public class Answer {

    private static final long LATENCY_PENALTY_LIMIT = 30000;
    private static final long LATENCY_UNSUCCESSFULL_LIMIT = 50000;
    private static final BigDecimal PENALTY_FACTOR = BigDecimal.valueOf(0.1);
    private static final BigDecimal BASE_SCORE = BigDecimal.valueOf(LATENCY_PENALTY_LIMIT * 2);
    private static final MathContext DECIMAL_SCALE = new MathContext(1, RoundingMode.HALF_UP);
    private static final MathContext ROUNDING_MODE = new MathContext(0, RoundingMode.HALF_UP);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_selected")
    private LetterOption optionSelected;

    @Column(name = "milliseconds_spent", nullable = false)
    private long millisecondsSpent;

    @Column(name = "error_reason")
    @JsonIgnore
    private String errorReason;

    @Column(name = "response_date")
    private LocalDate responseDate;

    private BigDecimal score = BigDecimal.ZERO;

    public void impactScore(Pregunta question) {
        if (isSuccess(question)) {
            score = calculateScoreBy(question);
            return;
        }
        errorReason = errorReason != null ? errorReason : UnsuccessReasons.INCORRECT_OPTION;
    }

    public BigDecimal getScore() {
        return score == null ? BigDecimal.ZERO : score;
    }

    private BigDecimal calculateScoreBy(Pregunta question) {
        BigDecimal scoreTemp = isPenalized() ? penalizedScore() : baseScore();

        return question.difficultyFactor().multiply(scoreTemp);
    }

    private boolean isSuccess(Pregunta question) {
        if (isLatencyLimitExeeded()) {
            errorReason = UnsuccessReasons.RESPONSE_TIME_EXEEDED;
        }

        return question.isSuccess(optionSelected) && !isLatencyLimitExeeded();
    }

    private boolean isLatencyLimitExeeded() {
        return millisecondsSpent > LATENCY_UNSUCCESSFULL_LIMIT;
    }

    private BigDecimal penalizedScore() {
        return baseScore().multiply(PENALTY_FACTOR);
    }

    private BigDecimal baseScore() {
        return BASE_SCORE.divide(BigDecimal.valueOf(millisecondsSpent), DECIMAL_SCALE).round(ROUNDING_MODE);
    }

    private boolean isPenalized() {
        return millisecondsSpent >= LATENCY_PENALTY_LIMIT;
    }
}
