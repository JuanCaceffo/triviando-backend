package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Borra salas y todo su contenido efímero cuando vence su retención.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomQuestionRepository questionRepository;
    private final RoomAnswerRepository answerRepository;
    private final RoomGenerationRepository generationRepository;
    private final RoomEventPublisher eventPublisher;
    private final RoomProperties properties;
    private final Clock clock;

    @Transactional
    @Scheduled(fixedDelayString = "${api.rooms.cleanup-ms:60000}")
    public void scheduledCleanup() {
        if (properties.isSchedulerEnabled()) {
            cleanup();
        }
    }

    @Transactional
    public int cleanup() {
        List<Room> rooms = roomRepository.findByExpiresAtBefore(LocalDateTime.now(clock));
        for (Room room : rooms) {
            eventPublisher.publishClosed(room.getId(), com.example.proyecto2025_BE.model.room.RoomCloseReason.EXPIRED);
            answerRepository.deleteByRoomId(room.getId());
            List<RoomQuestion> questions =
                    questionRepository.findByRoomIdOrderByPositionAsc(room.getId());
            questionRepository.deleteAll(questions);
            generationRepository.deleteByRoomId(room.getId());
            memberRepository.deleteByRoomId(room.getId());
            roomRepository.delete(room);
        }
        if (!rooms.isEmpty()) {
            log.info("Se eliminaron {} salas expiradas", rooms.size());
        }
        return rooms.size();
    }
}
