package com.example.proyecto2025_BE.views.rooms;

public record RoomReactionEvent(Long userId, String username, String emoji, long sentAtEpochMs) {
}
