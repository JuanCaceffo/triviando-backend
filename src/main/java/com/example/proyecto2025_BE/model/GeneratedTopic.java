package com.example.proyecto2025_BE.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado embebido en un job de generación. Se mapea luego al DTO Topics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedTopic {

    private String topic;
    private int size;
    private String emoji;
}
