package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.GenerationProgress;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.model.prompter.RoomFilePrompter;
import com.example.proyecto2025_BE.model.prompter.RoomPrompter;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomGeneration;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.service.DocumentContextBuilder;
import com.example.proyecto2025_BE.service.LLMApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lógica síncrona de generación de preguntas de sala. El worker la envuelve
 * con {@code @Async} para que los tests puedan invocarla directamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomGenerationProcessor {

    private final RoomGenerationRepository generationRepository;
    private final RoomRepository roomRepository;
    private final RoomGenerationService generationService;
    private final LLMApiClientService llmApiClientService;
    private final DocumentContextBuilder contextBuilder;
    private final RoomQuestionMapper questionMapper;
    private final RoomEventPublisher publisher;
    private final RoomProperties properties;
    private final Clock clock;

    public void process(Long generationId) {
        RoomGeneration generation = generationRepository.findById(generationId).orElse(null);
        if (generation == null || !JobStatus.PENDING.equals(generation.getStatus())) {
            return;
        }
        Room room = roomRepository.findById(generation.getRoomId()).orElse(null);
        if (room == null || !RoomStatus.PREPARING.equals(room.getStatus())) {
            return;
        }

        generationService.markProcessing(generationId);
        try {
            Prompter prompter = buildPrompter(generation, room);
            List<Question> questions = llmApiClientService.generateQuestions(prompter, room.getHostUserId());
            List<RoomQuestion> mapped = questionMapper.map(
                    room.getId(), questions, LocalDateTime.now(clock));
            if (mapped.isEmpty()) {
                generationService.fail(generationId, room.getId(), Exceptions.ROOM_GENERATION_EMPTY);
                publisher.publishSnapshot(room.getId());
                return;
            }
            generationService.complete(generationId, room.getId(), mapped);
            publisher.publishSnapshot(room.getId());
        } catch (Exception exception) {
            log.error("Falló la generación de la sala {}", room.getCode(), exception);
            generationService.fail(generationId, room.getId(), userFacingError(exception));
            publisher.publishSnapshot(room.getId());
        }
    }

    private Prompter buildPrompter(RoomGeneration generation, Room room) {
        String extractedText = generation.getExtractedText();
        if (extractedText != null && !extractedText.isBlank()) {
            String context = contextBuilder.buildContext(extractedText, percentage ->
                    generationService.updateProgress(
                            generation.getId(), GenerationProgress.fromSummaryPercentage(percentage)));
            return RoomFilePrompter.forDocument(
                    room.getTopic(), room.getHostUserId(), properties.getQuestionCount(), context);
        }
        return new RoomPrompter(
                room.getTopic(), room.getHostUserId(), properties.getQuestionCount());
    }

    private String userFacingError(Exception exception) {
        if (exception instanceof ValidationException) {
            return exception.getMessage();
        }
        return Exceptions.ROOM_GENERATION_FAILED;
    }
}
