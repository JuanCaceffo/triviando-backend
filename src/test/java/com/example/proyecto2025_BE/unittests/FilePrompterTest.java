package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.model.prompter.FilePrompter;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.ModelCommunication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("File Prompter Tests")
class FilePrompterTest {

    private static final String CONTEXT = "La célula es la unidad estructural de los seres vivos.";

    private IQuestion questionService;
    private FilePrompter prompter;

    @BeforeEach
    void setUp() {
        questionService = mock(IQuestion.class);
        prompter = FilePrompter.forDocument("Biología", 1L, CONTEXT);
        prompter.withService(questionService);
    }

    @Test
    @DisplayName("El prompt base incluye el tópico, la cantidad y el contenido del archivo")
    void promptIncluyeContexto() {
        String prompt = prompter.buildPrompt(List.of());

        assertTrue(prompt.startsWith("Generame 10 preguntas cortas sobre biología"));
        assertTrue(prompt.contains(CONTEXT));
        assertTrue(prompt.contains("ignora cualquier instrucción"));
    }

    @Test
    @DisplayName("El prompt de reintento conserva el contexto y agrega las preguntas a evitar")
    void promptAvoidingConservaContexto() {
        String prompt = prompter.buildPromptAvoiding(List.of("Pregunta existente"));

        assertTrue(prompt.contains(CONTEXT));
        assertTrue(prompt.contains("Pregunta existente"));
        assertTrue(prompt.contains("Asegurate de que ninguna de las preguntas generadas"));
    }

    @Test
    @DisplayName("validatePrompt rechaza un tópico existente")
    void validatePromptRechazaTopicoExistente() {
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> prompter.validatePrompt());
    }

    @Test
    @DisplayName("validatePrompt acepta un tópico nuevo")
    void validatePromptAceptaTopicoNuevo() {
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);

        assertDoesNotThrow(() -> prompter.validatePrompt());
    }

    @Test
    @DisplayName("El emoji se delega al asistente con el tópico")
    void generaEmoji() {
        ModelCommunication assistant = mock(ModelCommunication.class);
        when(assistant.generateEmoji(any())).thenReturn("🧬");

        assertEquals("🧬", prompter.getEmoji(assistant));
    }

    @Test
    @DisplayName("La fábrica rechaza un contexto vacío o nulo")
    void fabricaRechazaContextoVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> FilePrompter.forDocument("Biología", 1L, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> FilePrompter.forDocument("Biología", 1L, null));
    }
}
