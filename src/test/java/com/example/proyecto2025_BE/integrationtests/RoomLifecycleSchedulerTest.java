package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomGeneration;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.room.RoomCleanupService;
import com.example.proyecto2025_BE.service.room.RoomConnectionRegistry;
import com.example.proyecto2025_BE.service.room.RoomLifecycleScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_scheduler;DB_CLOSE_DELAY=-1")
@DisplayName("Room Lifecycle Scheduler Test")
class RoomLifecycleSchedulerTest {

    @Autowired
    private RoomLifecycleScheduler scheduler;
    @Autowired
    private RoomCleanupService cleanupService;
    @Autowired
    private RoomConnectionRegistry connectionRegistry;
    @Autowired
    private RoomProperties properties;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomQuestionRepository questionRepository;
    @Autowired
    private RoomGenerationRepository generationRepository;
    @Autowired
    private RoomAnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    private User host;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        questionRepository.deleteAll();
        generationRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        host = userRepository.save(User.builder()
                .username("room_host")
                .email("room_host@test.com")
                .password("encoded")
                .name("Room")
                .lastName("Host")
                .account(Account.PREMIUM)
                .build());
    }

    @Test
    @DisplayName("El tick cierra una ronda vencida y deja la sala en reveal")
    void tickClosesExpiredRound() {
        LocalDateTime now = LocalDateTime.now();
        Room room = roomRepository.save(room(RoomStatus.IN_PROGRESS, now.plusMinutes(60)));
        room.setQuestionCount(1);
        room.setCurrentQuestionIndex(0);
        room.setQuestionStartedAt(now.minusSeconds(40));
        room.setQuestionDeadlineAt(now.minusSeconds(10));
        roomRepository.save(room);
        questionRepository.save(question(room.getId()));

        assertTrue(scheduler.tick() > 0);

        Room closed = roomRepository.findById(room.getId()).orElseThrow();
        assertNotNull(closed.getRevealUntil());
    }

    @Test
    @DisplayName("El tick cierra una sala cuya expiración ya venció")
    void tickExpiresRoom() {
        Room room = roomRepository.save(room(RoomStatus.LOBBY, LocalDateTime.now().minusSeconds(1)));

        assertTrue(scheduler.tick() > 0);

        Room closed = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals(RoomStatus.CLOSED, closed.getStatus());
        assertEquals(RoomCloseReason.EXPIRED, closed.getCloseReason());
    }

    @Test
    @DisplayName("El tick cierra la sala si el anfitrión estuvo ausente más de la gracia")
    void tickClosesRoomWhenHostIsAbsent() {
        Room room = roomRepository.save(room(RoomStatus.LOBBY, LocalDateTime.now().plusMinutes(30)));
        connectionRegistry.markHostAbsentIfNeeded(room.getId(), Instant.now().minusSeconds(61));

        assertTrue(scheduler.tick() > 0);

        Room closed = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals(RoomStatus.CLOSED, closed.getStatus());
        assertEquals(RoomCloseReason.HOST_LEFT, closed.getCloseReason());
    }

    @Test
    @DisplayName("El cleanup programado borra una sala expirada dentro de su transacción")
    void scheduledCleanupRunsInTransaction() {
        Room room = roomRepository.save(room(RoomStatus.CLOSED, LocalDateTime.now().minusSeconds(1)));
        memberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(host.getId())
                .joinedAt(LocalDateTime.now())
                .build());
        questionRepository.save(question(room.getId()));
        generationRepository.save(RoomGeneration.builder()
                .roomId(room.getId())
                .status(JobStatus.COMPLETED)
                .progress(100)
                .createdAt(LocalDateTime.now())
                .build());

        properties.setSchedulerEnabled(true);
        try {
            cleanupService.scheduledCleanup();
        } finally {
            properties.setSchedulerEnabled(false);
        }

        assertTrue(roomRepository.findById(room.getId()).isEmpty());
        assertTrue(memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).isEmpty());
    }

    @Test
    @DisplayName("La limpieza borra la sala y todo su contenido efímero")
    void cleanupDeletesExpiredRoomAndChildren() {
        Room room = roomRepository.save(room(RoomStatus.CLOSED, LocalDateTime.now().minusSeconds(1)));
        memberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(host.getId())
                .joinedAt(LocalDateTime.now())
                .build());
        questionRepository.save(question(room.getId()));
        generationRepository.save(RoomGeneration.builder()
                .roomId(room.getId())
                .status(JobStatus.COMPLETED)
                .progress(100)
                .createdAt(LocalDateTime.now())
                .build());

        assertEquals(1, cleanupService.cleanup());

        assertTrue(roomRepository.findById(room.getId()).isEmpty());
        assertTrue(memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).isEmpty());
        assertTrue(questionRepository.findByRoomIdOrderByPositionAsc(room.getId()).isEmpty());
        assertTrue(generationRepository.findByRoomId(room.getId()).isEmpty());
    }

    private Room room(RoomStatus status, LocalDateTime expiresAt) {
        return Room.builder()
                .code("ABC234")
                .hostUserId(host.getId())
                .status(status)
                .source(RoomSource.TOPIC)
                .topic("Historia")
                .questionCount(0)
                .currentQuestionIndex(-1)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }

    private RoomQuestion question(Long roomId) {
        return RoomQuestion.builder()
                .roomId(roomId)
                .position(0)
                .enunciado("¿Capital de Francia?")
                .explicacion("París")
                .difficulty(Difficulty.LOW)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
