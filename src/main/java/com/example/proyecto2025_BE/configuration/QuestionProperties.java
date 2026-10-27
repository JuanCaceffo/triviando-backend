package com.example.proyecto2025_BE.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "api.questions")
@Data
public class QuestionProperties {
    /**
     * Cantidad de preguntas que se generan por tópico
     */
    private int questionsPerTopic;
}
