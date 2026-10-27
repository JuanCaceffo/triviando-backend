package com.example.proyecto2025_BE.views.questions;

import java.util.List;

import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.Option;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Pregunta generada")
public class QuestionResponse200 {

	@Schema(description = "ID de la pregunta", example = "42")
	private Long id;
	@Schema(description = "Tópico de la pregunta", example = "Inteligencia Artificial y Aprendizaje Automático")
    private String topico;
	@Schema(description = "Enunciado de la pregunta", example = "¿Qué es una 'red neuronal convolucional' (CNN) comúnmente utilizada para?")
    private String enunciado;
	@Schema(description = "Opciones a elegir", example = "Procesamiento de lenguaje natural")
	private List<Option> options;
	@Schema(description = "Feedback de la pregunta", example = "Procesamiento de lenguaje natural")
    private String explicacion;
	@Schema(description = "Dificultad de la pregunta", example = "HIGH")
    private Difficulty difficulty;
}
