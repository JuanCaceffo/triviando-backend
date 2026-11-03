package com.example.proyecto2025_BE.views.rooms;

import com.example.proyecto2025_BE.model.JobStatus;

public record RoomGenerationResponse(JobStatus status, int progress, String error) {
}
