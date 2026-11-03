package com.example.proyecto2025_BE.views.rooms;

public record RoomPlayerResponse(
        Long userId,
        String username,
        String name,
        int score,
        int correctCount,
        long totalResponseMs,
        boolean answeredCurrent,
        boolean host,
        boolean left,
        boolean connected,
        Integer pointsLastRound,
        Boolean correctLastRound,
        int rank) {
}
