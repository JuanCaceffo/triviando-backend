package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.prompter.QuestionPrompter;
import com.example.proyecto2025_BE.service.IQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Question Prompter Tests")
public class QuestionPrompterTest {

    private IQuestion questionService;
    private QuestionPrompter prompter;

    @BeforeEach
    void setup() {
        questionService = mock(IQuestion.class);
        prompter = QuestionPrompter.builder()
                .topic("Java")
                .userId(1L)
                .build();
        prompter.withService(questionService);
    }

    @Test
    @DisplayName("existingEnunciados devuelve todas las preguntas existentes del topico")
    void existingEnunciadosDevuelveTodas() {
        when(questionService.findByTopicoAndUserId("Java", 1L)).thenReturn(preguntas(35));

        assertEquals(35, prompter.existingEnunciados().size());
    }

    @Test
    @DisplayName("buildPrompt limita el contexto del prompt a 30 preguntas")
    void buildPromptLimitaElContextoA30() {
        when(questionService.findByTopicoAndUserId("Java", 1L)).thenReturn(preguntas(35));

        String prompt = prompter.buildPrompt(prompter.existingEnunciados());

        assertTrue(prompt.startsWith("Generame 5 preguntas cortas sobre java"));
        // 30 enunciados unidos por " | " -> 29 separadores
        assertEquals(29, countOccurrences(prompt, " | "));
    }

    @Test
    @DisplayName("buildPrompt con menos de 30 preguntas existentes incluye todas")
    void buildPromptConPocasPreguntas() {
        when(questionService.findByTopicoAndUserId("Java", 1L)).thenReturn(preguntas(2));

        String prompt = prompter.buildPrompt(prompter.existingEnunciados());

        assertEquals(1, countOccurrences(prompt, " | "));
    }

    @Test
    @DisplayName("buildPrompt sin existentes devuelve solo el prompt base")
    void buildPromptSinExistentes() {
        String prompt = prompter.buildPrompt(List.of());

        assertEquals("Generame 5 preguntas cortas sobre java", prompt);
    }

    @Test
    @DisplayName("buildPromptAvoiding no repite la seccion de contexto")
    void buildPromptAvoidingNoRepiteElContexto() {
        when(questionService.findByTopicoAndUserId("Java", 1L)).thenReturn(preguntas(35));

        String prompt = prompter.buildPromptAvoiding(List.of("Pregunta nueva a evitar"));

        assertTrue(prompt.startsWith("Generame 5 preguntas cortas sobre java"));
        assertTrue(prompt.contains("Asegurate de que ninguna de las preguntas generadas"));
        assertFalse(prompt.contains("Ya existen estas preguntas sobre el topico"));
    }

    @Test
    @DisplayName("buildPromptAvoiding acota la lista a evitar a 30 enunciados")
    void buildPromptAvoidingAcotaLaLista() {
        List<String> avoid = IntStream.range(0, 50)
                .mapToObj(i -> "Pregunta a evitar " + i)
                .toList();

        String prompt = prompter.buildPromptAvoiding(avoid);

        // 30 enunciados unidos por " | " -> 29 separadores
        assertEquals(29, countOccurrences(prompt, " | "));
        assertTrue(prompt.contains("Pregunta a evitar 0"));
    }

    private List<Pregunta> preguntas(int cantidad) {
        return IntStream.range(0, cantidad)
                .mapToObj(i -> Pregunta.builder().enunciado("Pregunta " + i).build())
                .toList();
    }

    private int countOccurrences(String text, String substring) {
        return (text.length() - text.replace(substring, "").length()) / substring.length();
    }
}
