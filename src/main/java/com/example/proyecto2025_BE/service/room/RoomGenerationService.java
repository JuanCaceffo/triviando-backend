package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomGeneration;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ciclo de vida del trabajo de generación de una sala.
 */
@Service
@RequiredArgsConstructor
public class RoomGenerationService {

    private final RoomGenerationRepository repository;
    private final RoomRepository roomRepository;
    private final RoomQuestionRepository questionRepository;
    private final ObjectProvider<RoomGenerationWorker> workerProvider;
    private final RoomProperties properties;
    private final Clock clock;

    @Transactional
    public RoomGeneration enqueue(Long roomId) {
        return enqueue(roomId, null);
    }

    @Transactional
    public RoomGeneration enqueue(Long roomId, String extractedText) {
        LocalDateTime now = LocalDateTime.now(clock);
        RoomGeneration generation = repository.save(RoomGeneration.builder()
                .roomId(roomId)
                .status(JobStatus.PENDING)
                .progress(0)
                .extractedText(extractedText)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Runnable trigger = () -> workerProvider.getObject().process(generation.getId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
        return generation;
    }

    @Transactional
    public void markProcessing(Long generationId) {
        repository.findById(generationId).ifPresent(generation -> {
            if (!JobStatus.PENDING.equals(generation.getStatus())) {
                return;
            }
            generation.setStatus(JobStatus.PROCESSING);
            generation.setProgress(5);
            generation.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(generation);
        });
    }

    @Transactional
    public void updateProgress(Long generationId, int progress) {
        repository.findById(generationId).ifPresent(generation -> {
            if (!JobStatus.PROCESSING.equals(generation.getStatus())
                    || progress <= generation.getProgress()) {
                return;
            }
            generation.setProgress(progress);
            generation.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(generation);
        });
    }

    @Transactional
    public void complete(Long generationId, Long roomId, List<RoomQuestion> questions) {
        LocalDateTime now = LocalDateTime.now(clock);
        Room room = roomRepository.findByIdForUpdate(roomId).orElse(null);
        RoomGeneration generation = repository.findById(generationId).orElse(null);
        if (generation == null || !JobStatus.PROCESSING.equals(generation.getStatus())) {
            return;
        }

        // El anfitrión pudo cancelar o la sala pudo expirar mientras el LLM trabajaba.
        if (room == null || !RoomStatus.PREPARING.equals(room.getStatus())) {
            generation.setStatus(JobStatus.FAILED);
            generation.setError("La sala se cerró durante la generación");
            generation.setExtractedText(null);
            generation.setUpdatedAt(now);
            repository.save(generation);
            return;
        }

        questionRepository.saveAll(questions);
        room.setQuestionCount(questions.size());
        room.setStatus(RoomStatus.LOBBY);
        room.setExpiresAt(now.plusMinutes(properties.getLobbyExpiryMinutes()));
        roomRepository.save(room);

        generation.setStatus(JobStatus.COMPLETED);
        generation.setProgress(100);
        generation.setExtractedText(null);
        generation.setError(null);
        generation.setUpdatedAt(now);
        repository.save(generation);
    }

    @Transactional
    public void fail(Long generationId, Long roomId, String message) {
        LocalDateTime now = LocalDateTime.now(clock);

        repository.findById(generationId).ifPresent(generation -> {
            if (!generation.isFinished()) {
                generation.setStatus(JobStatus.FAILED);
                generation.setError(message);
                generation.setExtractedText(null);
                generation.setUpdatedAt(now);
                repository.save(generation);
            }
        });

        roomRepository.findByIdForUpdate(roomId).ifPresent(room -> {
            if (!RoomStatus.PREPARING.equals(room.getStatus())) {
                return;
            }
            room.setStatus(RoomStatus.FAILED);
            room.setCloseReason(com.example.proyecto2025_BE.model.room.RoomCloseReason.GENERATION_FAILED);
            room.setExpiresAt(now.plusMinutes(properties.getClosedExpiryMinutes()));
            roomRepository.save(room);
        });
    }

    @Transactional
    public void expireIfStale(Long generationId) {
        repository.findById(generationId).ifPresent(generation -> {
            if (generation.isFinished()) {
                return;
            }
            generation.setStatus(JobStatus.FAILED);
            generation.setError(Exceptions.GENERATION_JOB_EXPIRED);
            generation.setExtractedText(null);
            generation.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(generation);
        });
    }
}
