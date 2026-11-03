package com.example.proyecto2025_BE.views.rooms;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomQuestionResponse(
        Long id,
        int number,
        String text,
        List<RoomOptionResponse> options,
        String correctOptionLetter,
        String correctOptionText,
        String explanation) {
}
