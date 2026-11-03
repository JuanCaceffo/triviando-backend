package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.dto.llm.QuestionOption;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce las preguntas del LLM al modelo efímero de sala, descartando
 * preguntas incompletas o sin una única opción correcta.
 */
@Component
public class RoomQuestionMapper {

    public List<RoomQuestion> map(Long roomId, List<Question> questions, LocalDateTime now) {
        List<RoomQuestion> result = new ArrayList<>();
        if (questions == null) {
            return result;
        }
        for (Question question : questions) {
            RoomQuestion mapped = mapOne(roomId, question, result.size(), now);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private RoomQuestion mapOne(Long roomId, Question question, int position, LocalDateTime now) {
        if (question == null || question.text() == null || question.text().isBlank()
                || question.correctOption() == null || question.correctOption().letter() == null) {
            return null;
        }

        Map<LetterOption, QuestionOption> byLetter = new LinkedHashMap<>();
        if (question.options() != null) {
            for (QuestionOption option : question.options()) {
                if (option != null && option.letter() != null && option.text() != null
                        && !option.text().isBlank()) {
                    byLetter.putIfAbsent(option.letter(), option);
                }
            }
        }

        QuestionOption correct = question.correctOption();
        if (!byLetter.containsKey(correct.letter())
                && correct.text() != null && !correct.text().isBlank()) {
            byLetter.put(correct.letter(), correct);
        }
        if (byLetter.size() < 2 || !byLetter.containsKey(correct.letter())) {
            return null;
        }

        RoomQuestion mapped = RoomQuestion.builder()
                .roomId(roomId)
                .position(position)
                .enunciado(question.text().trim())
                .explicacion(question.briefExplanationOfTheCorrectAnswer())
                .difficulty(question.difficulty() == null ? Difficulty.MEDIUM : question.difficulty())
                .createdAt(now)
                .build();

        List<RoomOption> options = new ArrayList<>();
        int optionPosition = 0;
        for (QuestionOption option : byLetter.values()) {
            options.add(RoomOption.builder()
                    .question(mapped)
                    .letter(option.letter())
                    .text(option.text().trim())
                    .correct(option.letter().equals(correct.letter()))
                    .positionOrder(optionPosition++)
                    .build());
        }
        mapped.setOptions(options);
        return mapped;
    }
}
