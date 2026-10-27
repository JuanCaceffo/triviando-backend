package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.migration.MigrationNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationNormalizerTest {

    @Test
    void normalizaMayusculasEspaciosYAcentos() {
        assertEquals(
                MigrationNormalizer.normalizedTopic("Historia"),
                MigrationNormalizer.normalizedTopic("  Historia  "));
        assertEquals(
                MigrationNormalizer.normalizedTopic("Programación orientada a objetos"),
                MigrationNormalizer.normalizedTopic("programacion orientada a objetos "));
    }

    @Test
    void eliminaPuntuacionEnLaClaveDeTopico() {
        assertEquals(
                "modelo de datos olap oltp",
                MigrationNormalizer.normalizedTopic("Modelo de datos, OLAP, OLTP"));
    }

    @Test
    void conservaElNombreVisibleSinEspaciosExtra() {
        assertEquals("Literatura", MigrationNormalizer.displayName("  Literatura  "));
        assertEquals("Autoconstrucción de casa de quincha",
                MigrationNormalizer.displayName("Autoconstrucción   de casa de quincha "));
    }

    @Test
    void elHashEsEstableYNoDependeDeAcentosNiMayusculas() {
        String conAcentos = MigrationNormalizer.enunciadoHash("¿Qué es Java?");
        String sinAcentos = MigrationNormalizer.enunciadoHash("que es java");

        assertEquals(conAcentos, sinAcentos);
        assertEquals(64, conAcentos.length());
        assertTrue(conAcentos.matches("[0-9a-f]{64}"));
    }

    @Test
    void elHashDistingueEnunciadosDistintos() {
        assertNotEquals(
                MigrationNormalizer.enunciadoHash("¿Qué es Java?"),
                MigrationNormalizer.enunciadoHash("¿Qué es Kotlin?"));
    }
}
