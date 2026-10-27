package com.example.proyecto2025_BE.controller;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.security.UserContext;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.LLMApiClientService;
import com.example.proyecto2025_BE.views.questions.QuestionResponse200;
import com.example.proyecto2025_BE.views.questions.requests.GenerateRequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/preguntas")
@Tag(name = "Preguntas Controller", description = "API para la gestión de preguntas")
public class QuestionController {

    private final IQuestion preguntasService;
    private final LLMApiClientService llmApiClientService;
    private final UserContext userContext;

    private Account getRole() {
        return userContext.getContextRole();
    }

    private Long getUserId() {
        return userContext.getContextUserId();
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtiene todas las preguntas o las preguntas por tópico",
            description = "Si se proporciona un parámetro 'topico', devuelve las preguntas asociadas a ese tópico. " +
                    "De lo contrario, devuelve todas las preguntas." +
                    "Si se proporciona una userId valido, filtrará las preguntas ya respondidas por el mismo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Listado de preguntas obtenido exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuestionResponse200.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND, content = @Content)
    })
    public List<Pregunta> getPreguntas(@RequestParam(value = "userId") Long userId,
                                       @RequestParam(value = "topico", required = false) String topico) {
        return preguntasService.obtenerPreguntasNoRespondidasPorTopico(userId, topico);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Obtiene una pregunta por su ID",
            description = "Devuelve una pregunta específica mediante su identificador. Lanza una excepción si no existe.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pregunta encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuestionResponse200.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND, content = @Content)
    })
    public Pregunta getPreguntaById(@PathVariable Long id) {
        return preguntasService.getPreguntaById(id);
    }

    @GetMapping("/topicos/{userId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Cuenta la cantidad de preguntas por tópico",
            description = "Devuelve una lista con la cantidad de preguntas agrupadas por tópico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo obtenido exitosamente")
    })
    public List<Topics> cantidadPreguntasPorTopico(@PathVariable long userId) {
        return preguntasService.contarPreguntasPorTopicoDeUsuario(userId);
    }

    @PostMapping("/generate")
    @Operation(summary = "Generar preguntas del LLM", description = "Generacion de preguntas según tópico",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Preguntas generadas exitosamente"),
            })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos para generar un set de preguntas sobre determinado topico",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GenerateRequestBody.class)))
    public List<Topics> generate(@RequestBody Prompter prompter) {
        return llmApiClientService.generate(prompter, this.getRole(), this.getUserId());
    }

    @PostMapping("/send-feedback")
    @Operation(summary = "Enviar feedback de la pregunta", description = "Enviar feedback de la pregunta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Feedback enviado exitosamente")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public void sendFeedback(@Validated @RequestBody FeedbackDTO feedbackDTO) {
        preguntasService.saveFeedback(feedbackDTO);
    }
}