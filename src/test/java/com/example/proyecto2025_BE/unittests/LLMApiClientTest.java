package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.dto.llm.QuestionList;
import com.example.proyecto2025_BE.model.dto.llm.QuestionOption;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.LLMApiClientService;
import com.example.proyecto2025_BE.service.ModelCommunication;
import com.example.proyecto2025_BE.service.QuestionDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("Ollama Api Client Tests")
public class LLMApiClientTest {

    private LLMApiClientService llmService;
    private ModelCommunication assistant;
    private IQuestion questionService;
    private QuestionList mockQuestions;

    @BeforeEach
    void setup() {
        questionService = mock(IQuestion.class);
        assistant = mock(ModelCommunication.class);
        llmService = new LLMApiClientService(assistant, questionService, new QuestionDeduplicationService());
        QuestionOption correct = new QuestionOption("Lenguaje de programación", LetterOption.A);
        QuestionOption wrong = new QuestionOption("Sistema operativo", LetterOption.B);
        QuestionOption wrong2 = new QuestionOption("Una Fruta", LetterOption.C);
        QuestionOption wrong3 = new QuestionOption("Un Framework", LetterOption.D);

        Question question = new Question(
                "¿Qué es Java?",
                List.of(correct, wrong, wrong2, wrong3),
                correct,
                Difficulty.LOW,
                "Java es un lenguaje de programación multiplataforma."
        );

        mockQuestions = new QuestionList(List.of(question));
    }

    @Test
    void generateTopic() {
        Prompter prompter = mock(Prompter.class);
        when(prompter.withService(questionService)).thenReturn(prompter);
        when(prompter.buildPrompt(anyList())).thenReturn("mock prompt");
        when(prompter.existingEnunciados()).thenReturn(List.of());
        when(prompter.getEmoji(any())).thenReturn("🔥");

        // Simular que no lanza excepción
        doNothing().when(prompter).validateGeneration(anyLong());
        doNothing().when(prompter).validatePrompt();

        // Mockear la respuesta del assistant
        when(assistant.generateQuestions(anyString())).thenReturn(mockQuestions);

        List<?> result = llmService.generate(prompter, Account.FREE, 1L);

        assertNotNull(result);
        verify(prompter, times(1)).validatePrompt();
        verify(prompter, times(1)).validateGeneration(1L);
        verify(prompter, times(1)).getEmoji(assistant);
        verify(questionService, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Sin duplicados guarda la tanda completa sin reintentar")
    void generateSinDuplicadosNoReintenta() {
        Prompter prompter = mock(Prompter.class);
        when(prompter.withService(questionService)).thenReturn(prompter);
        when(prompter.buildPrompt(anyList())).thenReturn("mock prompt");
        when(prompter.existingEnunciados()).thenReturn(List.of("¿Qué es Python?"));
        when(prompter.getEmoji(any())).thenReturn("🔥");
        doNothing().when(prompter).validatePrompt();

        when(assistant.generateQuestions("mock prompt")).thenReturn(mockQuestions);

        llmService.generate(prompter, Account.FREE, 1L);

        verify(assistant, times(1)).generateQuestions(anyString());
        verify(prompter, never()).buildPromptAvoiding(anyList());
        verify(questionService, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Con duplicados filtra, reintenta una sola vez y guarda solo las no duplicadas")
    void generateFiltraDuplicadosYReintentaUnaVez() {
        Prompter prompter = mock(Prompter.class);
        when(prompter.withService(questionService)).thenReturn(prompter);
        when(prompter.buildPrompt(anyList())).thenReturn("mock prompt");
        when(prompter.buildPromptAvoiding(anyList())).thenReturn("mock retry prompt");
        when(prompter.existingEnunciados()).thenReturn(List.of("¿Qué es Java?"));
        when(prompter.getEmoji(any())).thenReturn("🔥");
        doNothing().when(prompter).validatePrompt();

        QuestionOption correct = new QuestionOption("Opción correcta", LetterOption.A);
        QuestionOption wrong = new QuestionOption("Opción incorrecta", LetterOption.B);
        List<QuestionOption> options = List.of(correct, wrong);

        Question duplicadaExistente = new Question("¿Qué es Java?", options, correct, Difficulty.LOW, "exp");
        Question nueva = new Question("¿Quién creó Java?", options, correct, Difficulty.LOW, "exp");
        Question nuevaEnElReintento = new Question("¿Qué es un framework?", options, correct, Difficulty.LOW, "exp");
        Question otraDuplicada = new Question("¿Que es java?", options, correct, Difficulty.LOW, "exp");

        when(assistant.generateQuestions("mock prompt")).thenReturn(new QuestionList(List.of(duplicadaExistente, nueva)));
        when(assistant.generateQuestions("mock retry prompt"))
                .thenReturn(new QuestionList(List.of(nuevaEnElReintento, otraDuplicada)));

        llmService.generate(prompter, Account.FREE, 1L);

        verify(assistant, times(1)).generateQuestions("mock prompt");
        verify(assistant, times(1)).generateQuestions("mock retry prompt");

        // La primera tanda aceptada va primero en la lista a evitar (prioridad del tope del prompt)
        ArgumentCaptor<List<String>> avoidCaptor = ArgumentCaptor.forClass(List.class);
        verify(prompter).buildPromptAvoiding(avoidCaptor.capture());
        List<String> avoid = avoidCaptor.getValue();
        assertEquals("¿Quién creó Java?", avoid.get(0));
        assertTrue(avoid.contains("¿Qué es Java?"));

        ArgumentCaptor<List<Pregunta>> preguntasGuardadas = ArgumentCaptor.forClass(List.class);
        verify(questionService).saveAll(preguntasGuardadas.capture());

        List<String> enunciados = preguntasGuardadas.getValue().stream().map(Pregunta::getEnunciado).toList();
        assertEquals(2, enunciados.size());
        assertTrue(enunciados.contains("¿Quién creó Java?"));
        assertTrue(enunciados.contains("¿Qué es un framework?"));
    }
}
