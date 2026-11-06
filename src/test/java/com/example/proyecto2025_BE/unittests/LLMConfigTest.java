package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.LLMConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("LLM Config Tests")
class LLMConfigTest {

    private static final String MODEL = "gpt-6-luna";

    private LLMConfig config(String reasoningEffort) {
        LLMConfig config = new LLMConfig();
        config.setApiKey("test-key");
        config.setModelName(MODEL);
        config.setReasoningEffort(reasoningEffort);
        config.setSummarizerReasoningEffort("none");
        config.setLogRequests(false);
        config.setLogResponses(false);
        return config;
    }

    @Test
    @DisplayName("La generación usa gpt-6-luna con reasoning medium y sin temperature")
    void generationUsesGpt6LunaWithMediumReasoning() {
        OpenAiChatModel model = config("medium").generationChatModel();

        assertEquals(MODEL, model.defaultRequestParameters().modelName());
        assertEquals("medium", model.defaultRequestParameters().reasoningEffort());
        assertNull(model.defaultRequestParameters().temperature());
    }

    @Test
    @DisplayName("El resumidor usa reasoning none y conserva la temperatura baja")
    void summarizerUsesNoReasoningAndKeepsTemperature() {
        OpenAiChatModel model = config("medium").summarizerChatModel();

        assertEquals(MODEL, model.defaultRequestParameters().modelName());
        assertEquals("none", model.defaultRequestParameters().reasoningEffort());
        assertEquals(0.2, model.defaultRequestParameters().temperature(), 0.0001);
    }

    @Test
    @DisplayName("La temperature no se envía cuando el reasoning está habilitado")
    void temperatureIsOmittedWhenReasoningIsEnabled() {
        LLMConfig config = config("none");
        config.setSummarizerReasoningEffort("medium");

        assertNull(config.summarizerChatModel().defaultRequestParameters().temperature());
    }
}
