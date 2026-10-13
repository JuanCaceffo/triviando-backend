package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.service.QuestionDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Question Deduplication Service Tests")
public class QuestionDeduplicationServiceTest {

    private QuestionDeduplicationService deduplicationService;

    @BeforeEach
    void setup() {
        deduplicationService = new QuestionDeduplicationService();
    }

    private Question question(String text) {
        return new Question(text, List.of(), null, Difficulty.LOW, "explicacion");
    }

    @Test
    @DisplayName("Normalize quita acentos, puntuacion y espacios de mas")
    void normalizeQuitaAcentosYPuntuacion() {
        assertEquals("que es java", QuestionDeduplicationService.normalize("¿Qué es Java?"));
        assertEquals("quien creo java", QuestionDeduplicationService.normalize("  ¿Quién   creó Java!! "));
        assertEquals("", QuestionDeduplicationService.normalize(null));
    }

    @Test
    @DisplayName("Filtra duplicados exactos dentro de la misma tanda")
    void removeDuplicatesFiltraDuplicadosDentroDelLote() {
        List<Question> candidates = List.of(
                question("¿Qué es Java?"),
                question("¿Qué es Java"),
                question("¿Qué es Python?"));

        List<Question> result = deduplicationService.removeDuplicates(candidates, List.of());

        assertEquals(2, result.size());
        assertEquals("¿Qué es Java?", result.get(0).text());
        assertEquals("¿Qué es Python?", result.get(1).text());
    }

    @Test
    @DisplayName("Filtra preguntas que coinciden con las ya existentes")
    void removeDuplicatesFiltraContraPreguntasExistentes() {
        List<Question> candidates = List.of(
                question("¿Qué es Java?"),
                question("¿Cuáles son sus tipos primitivos?"));
        List<String> existing = List.of("¿Qué es java?");

        List<Question> result = deduplicationService.removeDuplicates(candidates, existing);

        assertEquals(1, result.size());
        assertEquals("¿Cuáles son sus tipos primitivos?", result.get(0).text());
    }

    @Test
    @DisplayName("Filtra parafasis casi identicas (Jaccard >= 0.8)")
    void removeDuplicatesFiltraParafasisCasiIdenticas() {
        // interseccion de tokens: 4 / union: 5 = 0.8 -> se filtra como duplicada
        List<Question> candidates = List.of(question("¿Qué es el lenguaje Java?"));
        List<String> existing = List.of("Que es lenguaje Java");

        List<Question> result = deduplicationService.removeDuplicates(candidates, existing);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Mantiene preguntas distintas del mismo topico")
    void removeDuplicatesMantienePreguntasDistintas() {
        List<Question> candidates = List.of(
                question("¿Qué es Java?"),
                question("¿Quién creó Java?"),
                question("¿Qué es un framework?"));

        List<Question> result = deduplicationService.removeDuplicates(candidates, List.of());

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Con lista de candidatas vacia devuelve vacia")
    void removeDuplicatesListaVacia() {
        assertTrue(deduplicationService.removeDuplicates(List.of(), List.of("¿Qué es Java?")).isEmpty());
    }
}
