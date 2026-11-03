package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ForbiddenException;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.service.room.RoomCodeGenerator;
import com.example.proyecto2025_BE.service.room.RoomGenerationService;
import com.example.proyecto2025_BE.service.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Room Service Rules Test")
class RoomServiceRulesTest {

    private RoomRepository roomRepository;
    private RoomMemberRepository memberRepository;
    private RoomGenerationService generationService;
    private RoomCodeGenerator codeGenerator;
    private RoomService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        memberRepository = mock(RoomMemberRepository.class);
        generationService = mock(RoomGenerationService.class);
        codeGenerator = mock(RoomCodeGenerator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        service = new RoomService(
                roomRepository, memberRepository, generationService, codeGenerator,
                new RoomProperties(), mock(com.example.proyecto2025_BE.service.document.DocumentTextExtractionService.class), clock);
    }

    @Test
    @DisplayName("Rechaza crear una sala sin Premium")
    void rejectsFreeHost() {
        assertThrows(ForbiddenException.class,
                () -> service.createTopicRoom("Historia", 1L, com.example.proyecto2025_BE.model.Account.FREE));
    }

    @Test
    @DisplayName("Rechaza una segunda sala activa del mismo anfitrión")
    void rejectsSecondActiveRoom() {
        when(roomRepository.existsByHostUserIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createTopicRoom("Historia", 1L, com.example.proyecto2025_BE.model.Account.PREMIUM));
    }

    @Test
    @DisplayName("Rechaza unirse a una partida ya iniciada")
    void rejectsJoinAfterStart() {
        Room room = Room.builder()
                .id(1L).code("ABC123").hostUserId(1L)
                .status(RoomStatus.IN_PROGRESS).topic("Historia")
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(roomRepository.findByCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.joinRoom("ABC123", 2L));
    }

    @Test
    @DisplayName("Rechaza al jugador que ya está en otra sala activa")
    void rejectsPlayerInAnotherRoom() {
        Room room = Room.builder()
                .id(1L).code("ABC123").hostUserId(1L)
                .status(RoomStatus.LOBBY).topic("Historia")
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(roomRepository.findByCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(memberRepository.existsActiveMembershipInOtherRoom(eq(2L), eq(1L), any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.joinRoom("ABC123", 2L));
    }

    @Test
    @DisplayName("Un FREE puede unirse a una sala lobby")
    void allowsFreeJoin() {
        Room room = Room.builder()
                .id(1L).code("ABC123").hostUserId(1L)
                .status(RoomStatus.LOBBY).topic("Historia")
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(roomRepository.findByCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(memberRepository.countByRoomIdAndLeftAtIsNull(1L)).thenReturn(1L);
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var member = service.joinRoom("ABC123", 2L);

        assertEquals(2L, member.getUserId());
        verify(memberRepository).save(any());
    }
}
