package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.migration.LegacyDocumentMapper;
import com.example.proyecto2025_BE.migration.LegacyGenerationJob;
import com.example.proyecto2025_BE.migration.LegacyQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDocumentMapperTest {

    private static final ObjectId ID = new ObjectId("6ac5b4cc5c468a3d7e4684f2");

    @Test
    void mapeaUnaPreguntaRealSinCreatedAt() {
        LegacyQuestion question = LegacyDocumentMapper.toQuestion(questionDocument(null));

        assertEquals("6ac5b4cc5c468a3d7e4684f2", question.objectId());
        assertEquals(4L, question.userId());
        assertEquals("Yamaha R3", question.topic());
        assertEquals("B", question.correctLetter());
        assertEquals(4, question.options().size());
        assertEquals("LOW", question.difficulty());
        assertEquals("🏍️", question.emoji());

        // Sin createdAt, el timestamp sale del ObjectId y no de now().
        assertNotNull(question.createdAt());
        assertTrue(question.createdAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void respetaCreatedAtCuandoExiste() {
        Date date = Date.from(java.time.Instant.parse("2026-01-02T03:04:05Z"));

        LegacyQuestion question = LegacyDocumentMapper.toQuestion(questionDocument(date));

        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), question.createdAt());
    }

    @Test
    void convierteUserIdEnteroALong() {
        Document document = questionDocument(null).append("userId", 7);

        assertEquals(7L, LegacyDocumentMapper.toQuestion(document).userId());
    }

    @Test
    void serializaElResultadoDeUnJob() {
        Document document = new Document("_id", ID)
                .append("userId", 4L)
                .append("role", "PREMIUM")
                .append("topic", "Futbol")
                .append("status", "COMPLETED")
                .append("progress", 100)
                .append("result", List.of(new Document("topic", "Futbol").append("size", 10).append("emoji", "⚽")))
                .append("createdAt", new Date());

        LegacyGenerationJob job = LegacyDocumentMapper.toJob(document, new ObjectMapper());

        assertEquals(4L, job.userId());
        assertEquals(100, job.progress());
        assertTrue(job.resultJson().contains("\"size\":10"));
    }

    private Document questionDocument(Date createdAt) {
        Document question = new Document("_id", ID)
                .append("topico", "Yamaha R3")
                .append("userId", 4L)
                .append("emoji", "🏍️")
                .append("enunciado", "¿Cuál es la cilindrada del motor de la Yamaha R3?")
                .append("options", List.of(
                        new Document("letter", "A").append("text", "250 cc"),
                        new Document("letter", "B").append("text", "300 cc"),
                        new Document("letter", "C").append("text", "400 cc"),
                        new Document("letter", "D").append("text", "500 cc")))
                .append("correctOption", new Document("letter", "B").append("text", "300 cc"))
                .append("explicacion", "La Yamaha R3 tiene 321 cc.")
                .append("difficulty", "LOW");

        if (createdAt != null) {
            question.append("createdAt", createdAt);
        }
        return question;
    }
}
