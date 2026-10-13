package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("User Racha Test")
public class UserRachaTest {

    @Test
    @DisplayName("Incrementa la racha y actualiza la fecha si la ultima actividad fue ayer")
    void debeIncrementarRachaYActualizarFecha() {
        User user = new User();
        user.setRachaActual(3);
        user.setUltimaActividad(LocalDate.now().minusDays(1));

        user.actualizarRacha();

        assertEquals(4, user.getRachaActual());
        assertEquals(LocalDate.now(), user.getUltimaActividad());
    }

    @Test
    @DisplayName("Resetea la racha si hubo dias salteados")
    void debeResetearRachaSiHuboDiasSalteados() {
        User user = new User();
        user.setRachaActual(5);
        user.setUltimaActividad(LocalDate.now().minusDays(2));

        user.actualizarRacha();

        assertEquals(1, user.getRachaActual());
        assertEquals(LocalDate.now(), user.getUltimaActividad());
    }

    @Test
    @DisplayName("Inicia la racha en 1 si no habia ultima actividad")
    void debeIniciarRachaSiNoHabiaUltimaActividad() {
        User user = new User();
        user.setRachaActual(0);
        user.setUltimaActividad(null);

        user.actualizarRacha();

        assertEquals(1, user.getRachaActual());
        assertEquals(LocalDate.now(), user.getUltimaActividad());
    }

    @Test
    @DisplayName("No incrementa la racha si ya se respondio hoy")
    void noDebeIncrementarRachaSiUltimaActividadEsHoy() {
        User user = new User();
        user.setRachaActual(2);
        user.setUltimaActividad(LocalDate.now());

        user.actualizarRacha();

        assertEquals(2, user.getRachaActual());
        assertEquals(LocalDate.now(), user.getUltimaActividad());
    }
}
