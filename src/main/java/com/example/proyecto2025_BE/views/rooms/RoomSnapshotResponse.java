package com.example.proyecto2025_BE.views.rooms;

import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomSnapshotResponse(
        String code,
        RoomStatus status,
        String topic,
        RoomSource source,
        RoomHostResponse host,
        List<RoomPlayerResponse> players,
        int maxPlayers,
        int questionCount,
        int roundSeconds,
        int revealSeconds,
        Integer currentQuestionNumber,
        RoomQuestionResponse currentQuestion,
        Long questionDeadlineEpochMs,
        Long revealUntilEpochMs,
        long serverTimeEpochMs,
        RoomGenerationResponse generation,
        MeResponse me,
        TeamSummaryResponse teamSummary,
        RoomCloseReason closeReason) {
}
