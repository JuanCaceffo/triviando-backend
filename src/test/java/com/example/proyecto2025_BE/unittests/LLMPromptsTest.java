package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.constants.LLM;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LLM Prompts Tests")
class LLMPromptsTest {

    @Test
    @DisplayName("El system prompt exige variar y equilibrar la opción correcta")
    void systemPromptExigeVariarLaOpcionCorrecta() {
        String prompt = LLM.TEMPLATE_SYSTEM_PROMPT;

        assertTrue(prompt.contains("no uses siempre la A"));
        assertTrue(prompt.contains("equilibrada entre A, B, C y D"));
        assertTrue(prompt.contains("distintas posiciones"));
        assertTrue(prompt.contains("letras distintas (A, B, C y D), sin repetir ninguna"));
        assertTrue(prompt.contains("correctOption"));
        assertTrue(prompt.contains("copia exacta (misma letra y mismo texto)"));
    }

    @Test
    @DisplayName("El prompt de archivos refuerza la variación de la opción correcta")
    void promptDeArchivosRefuerzaLaVariacion() {
        assertTrue(LLM.TEMPLATE_FILE_PROMPT.contains("sin favorecer la A"));
        assertTrue(LLM.TEMPLATE_FILE_PROMPT.contains("Variá la letra y la posición"));
    }
}
