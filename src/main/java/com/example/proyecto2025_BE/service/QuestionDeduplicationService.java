package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.dto.llm.Question;
import org.springframework.stereotype.Service;

import com.example.proyecto2025_BE.utils.ContentNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionDeduplicationService {

    static final double SIMILARITY_THRESHOLD = 0.8;

    /**
     * Filtra las preguntas candidatas que son duplicados (por igualdad exacta normalizada
     * o por similitud de tokens >= {@link #SIMILARITY_THRESHOLD}) de alguna de las
     * preguntas a evitar, o de otra candidata ya aceptada dentro de la misma tanda.
     */
    public List<Question> removeDuplicates(List<Question> candidates, List<String> enunciadosToAvoid) {
        List<String> seen = new ArrayList<>();
        enunciadosToAvoid.forEach(enunciado -> seen.add(normalize(enunciado)));

        List<Question> uniqueQuestions = new ArrayList<>();
        for (Question candidate : candidates) {
            String normalized = normalize(candidate.text());
            if (normalized.isEmpty() || isDuplicate(normalized, seen)) {
                continue;
            }
            seen.add(normalized);
            uniqueQuestions.add(candidate);
        }
        return uniqueQuestions;
    }

    public static String normalize(String text) {
        return ContentNormalizer.normalize(text);
    }

    private boolean isDuplicate(String normalized, List<String> seen) {
        return seen.stream().anyMatch(existing -> areSimilar(normalized, existing));
    }

    private boolean areSimilar(String candidate, String existing) {
        if (candidate.equals(existing)) {
            return true;
        }
        Set<String> candidateTokens = tokens(candidate);
        Set<String> existingTokens = tokens(existing);
        if (candidateTokens.isEmpty() || existingTokens.isEmpty()) {
            return false;
        }
        Set<String> intersection = new HashSet<>(candidateTokens);
        intersection.retainAll(existingTokens);
        Set<String> union = new HashSet<>(candidateTokens);
        union.addAll(existingTokens);
        return (double) intersection.size() / union.size() >= SIMILARITY_THRESHOLD;
    }

    private Set<String> tokens(String normalized) {
        return new HashSet<>(Arrays.asList(normalized.split(" ")));
    }
}
