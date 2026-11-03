package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.service.room.RoomEventPublisher;
import com.example.proyecto2025_BE.service.room.RoomReactionService;
import com.example.proyecto2025_BE.service.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Room Reaction Service Test")
class RoomReactionServiceTest {

    private RoomService roomService;
    private RoomEventPublisher publisher;
    private Clock clock;
    private RoomReactionService service;
    private Room room;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        publisher = mock(RoomEventPublisher.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T12:00:00Z"));

        RoomProperties properties = new RoomProperties();
        properties.setReactionCooldownMs(2_000);
        service = new RoomReactionService(roomService, publisher, properties, clock);

        room = Room.builder()
                .id(1L)
                .code("ABC123")
                .hostUserId(1L)
                .status(RoomStatus.IN_PROGRESS)
                .source(RoomSource.TOPIC)
                .topic("Historia")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(60))
                .build();

        when(roomService.getByCode("ABC123")).thenReturn(room);
        when(roomService.requireMember(eq("ABC123"), eq(2L)))
                .thenReturn(RoomMember.builder().roomId(1L).userId(2L).build());
    }

    @Test
    @DisplayName("Publica una reacción válida y aplica cooldown")
    void publishesValidReactionAndEnforcesCooldown() {
        service.react("ABC123", 2L, "🔥");
        verify(publisher).publishReaction(1L, 2L, "🔥");

        assertThrows(ConflictException.class, () -> service.react("ABC123", 2L, "😂"));

        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T12:00:03Z"));
        service.react("ABC123", 2L, "😂");
        verify(publisher).publishReaction(1L, 2L, "😂");
    }

    @Test
    @DisplayName("Rechaza emojis fuera de la whitelist y salas terminales")
    void rejectsInvalidEmojiAndTerminalRoom() {
        assertThrows(ValidationException.class, () -> service.react("ABC123", 2L, "💣"));
        verify(publisher, never()).publishReaction(1L, 2L, "💣");

        room.setStatus(RoomStatus.CLOSED);
        assertThrows(ConflictException.class, () -> service.react("ABC123", 2L, "🔥"));
        verify(publisher, never()).publishReaction(1L, 2L, "🔥");
    }
}
