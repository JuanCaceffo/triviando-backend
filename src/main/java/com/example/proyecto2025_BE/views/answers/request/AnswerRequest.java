package com.example.proyecto2025_BE.views.answers.request;

import com.example.proyecto2025_BE.model.LetterOption;

import com.example.proyecto2025_BE.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Representa los datos de entrada de la pregunta respondida")
public class AnswerRequest {
	 	
	@Schema(description = "Id de pregunta respondida", example = "42")
	private Long questionId;
	@Schema(description = "Usuario con su información",
			example = "{\"id\": 123}")
	private User user;
	@Schema(description = "Letra de la opcion seleccionada", example = "A")
    private LetterOption optionSelected;
	@Schema(description = "Tiempo en milisegundos empleados en responder", example = "25000")
    private long millisecondsSpent;
}
