package com.example.proyecto2025_BE.controller;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.security.UserContext;
import com.example.proyecto2025_BE.service.IDocumentGeneration;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/preguntas/document")
@Tag(name = "Document Generation Controller", description = "Generación asíncrona de preguntas a partir de archivos")
public class DocumentGenerationController {

    private final IDocumentGeneration documentGeneration;
    private final UserContext userContext;

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Encola la generación de un tópico a partir de un archivo",
            description = "Acepta PDF, DOCX, TXT o MD de hasta 10 MB. Devuelve un jobId para consultar el progreso.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Generación encolada exitosamente"),
            @ApiResponse(responseCode = "400", description = Exceptions.INVALID_FILE_TYPE, content = @Content),
            @ApiResponse(responseCode = "409", description = Exceptions.GENERATION_JOB_IN_PROGRESS, content = @Content),
            @ApiResponse(responseCode = "413", description = "El archivo supera el tamaño máximo permitido de 10 MB", content = @Content)
    })
    public ResponseEntity<GenerationJobCreatedResponse> generate(
            @Parameter(description = "Archivo PDF, DOCX, TXT o MD (máximo 10 MB)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Nombre del tópico a crear", required = true)
            @RequestParam("topic") String topic) {

        GenerationJobCreatedResponse response = documentGeneration.enqueue(
                topic, file, userContext.getContextRole(), userContext.getContextUserId());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/preguntas/document/jobs/{jobId}")
                .buildAndExpand(response.jobId())
                .toUri();

        return ResponseEntity.accepted().location(location).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Consulta el estado de una generación desde archivo",
            description = "Devuelve el estado, el progreso y, si terminó, el tópico generado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = Exceptions.GENERATION_JOB_NOT_FOUND, content = @Content)
    })
    public GenerationJobResponse getJob(@PathVariable Long jobId) {
        return documentGeneration.getJob(jobId, userContext.getContextUserId());
    }
}
