package com.example.proyecto2025_BE.views.rooms;

public record TeamSummaryResponse(
        int totalCorrect,
        int totalAnswers,
        int totalQuestions,
        int participants,
        double accuracy,
        RoomPlayerResponse bestPlayer) {
}
