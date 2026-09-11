package com.example.proyecto2025_BE.model.dto.login;

import com.example.proyecto2025_BE.views.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Builder;

@Builder
public record LoginResponseDTO(
        @JsonView(Views.UpdateUser.class)
        String token
) {
}
