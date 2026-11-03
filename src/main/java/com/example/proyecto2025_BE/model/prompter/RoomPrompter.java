package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.service.ModelCommunication;

import java.util.List;

/**
 * Prompter de una sala efímera. No valida la existencia del tópico ni genera
 * emoji: el contenido no se guarda en la biblioteca del usuario.
 */
public class RoomPrompter extends Prompter {

    private final int questionCount;

    public RoomPrompter(String topic, Long userId, int questionCount) {
        this.topic = topic;
        this.userId = userId;
        this.questionCount = questionCount;
    }

    @Override
    protected String questionsQuantity() {
        return String.valueOf(questionCount);
    }

    @Override
    public void validatePrompt() {
        if (topic == null || topic.isBlank()) {
            throw ValidationException.build(Exceptions.ROOM_TOPIC_REQUIRED);
        }
    }

    @Override
    public String getEmoji(ModelCommunication assistant) {
        return "🎯";
    }

    @Override
    public List<String> existingEnunciados() {
        return List.of();
    }
}
