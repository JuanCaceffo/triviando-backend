package com.example.proyecto2025_BE.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Reglas y tiempos de las salas de trivia en vivo.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "api.rooms")
public class RoomProperties {

    private int maxPlayers = 40;
    private int questionCount = 10;
    private int roundSeconds = 30;
    private int revealSeconds = 8;
    private int minPlayers = 2;
    private int hostGraceSeconds = 60;
    private int preparingExpiryMinutes = 30;
    private int lobbyExpiryMinutes = 30;
    private int inProgressExpiryMinutes = 60;
    private int finishedExpiryMinutes = 15;
    private int closedExpiryMinutes = 5;
    private long sseTimeoutMs = 7_200_000L;
    private long heartbeatMs = 15_000L;
    private long reactionCooldownMs = 2_000L;
    private boolean schedulerEnabled = true;
}
