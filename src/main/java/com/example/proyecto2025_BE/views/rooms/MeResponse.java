package com.example.proyecto2025_BE.views.rooms;

import com.example.proyecto2025_BE.model.LetterOption;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(
        Long userId,
        boolean host,
        boolean answeredCurrent,
        LetterOption selectedOption,
        Integer pointsLastRound,
        Boolean correctLastRound,
        int rank) {
}
