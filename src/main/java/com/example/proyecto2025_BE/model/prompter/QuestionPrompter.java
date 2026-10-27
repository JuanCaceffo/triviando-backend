package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.service.ModelCommunication;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@JsonTypeName("questionPrompter")
@NoArgsConstructor
public class QuestionPrompter extends Prompter {

    @Override
    protected String questionsQuantity() {
        return "5";
    }

    @Override
    public List<String> existingEnunciados() {
        // Todas las preguntas existentes del topico: el dedup las ve completas en memoria,
        // el recorte para el prompt lo hace buildPrompt/buildPromptAvoiding
        return question.findByTopicoAndUserId(getTopic(), getUserId()).stream()
                .map(Pregunta::getEnunciado)
                .toList();
    }

    @Override
    public void validatePrompt() {
        if (!super.existsTopic()) {
            throw ConflictException.build("No se puede generar preguntas para un topico que no existe");
        }
    }

    @Override
    public String getEmoji(ModelCommunication assistant) {
        return super.question.getTopicFromQuestion(getTopic(), getUserId());
    }
}