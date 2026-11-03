package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.constants.LLM;

/**
 * Prompter de sala a partir del contenido resumido de un archivo.
 */
public class RoomFilePrompter extends RoomPrompter {

    private final String summarizedContext;

    private RoomFilePrompter(String topic, Long userId, int questionCount, String summarizedContext) {
        super(topic, userId, questionCount);
        this.summarizedContext = summarizedContext;
    }

    public static RoomFilePrompter forDocument(
            String topic, Long userId, int questionCount, String summarizedContext) {
        if (summarizedContext == null || summarizedContext.isBlank()) {
            throw new IllegalArgumentException("El contexto resumido del documento es obligatorio");
        }
        return new RoomFilePrompter(topic, userId, questionCount, summarizedContext);
    }

    @Override
    protected String basePrompt() {
        return String.format(LLM.TEMPLATE_FILE_PROMPT, questionsQuantity(), topic.toLowerCase(), summarizedContext);
    }
}
