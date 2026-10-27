package com.example.proyecto2025_BE.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * Traduce documentos BSON crudos a los snapshots del migrador.
 * No depende de las entidades Mongo del runtime.
 */
public final class LegacyDocumentMapper {

    private LegacyDocumentMapper() {
    }

    public static LegacyQuestion toQuestion(Document document) {
        Document correctOption = document.get("correctOption", Document.class);
        List<LegacyOption> options = document.getList("options", Document.class) == null
                ? List.of()
                : document.getList("options", Document.class).stream()
                        .map(LegacyDocumentMapper::toOption)
                        .toList();

        return new LegacyQuestion(
                objectId(document),
                toLong(document.get("userId")),
                document.getString("topico"),
                document.getString("emoji"),
                document.getString("enunciado"),
                options,
                correctOption == null ? null : correctOption.getString("letter"),
                document.getString("explicacion"),
                document.getString("difficulty"),
                dateTimeOrObjectId(document, "createdAt")
        );
    }

    public static LegacyFeedback toFeedback(Document document) {
        return new LegacyFeedback(
                objectId(document),
                toLong(document.get("userId")),
                document.getString("questionId"),
                document.getString("feedbackType"),
                document.getString("description"),
                dateTimeOrObjectId(document, "createdAt")
        );
    }

    public static LegacyGenerationJob toJob(Document document, ObjectMapper objectMapper) {
        return new LegacyGenerationJob(
                objectId(document),
                toLong(document.get("userId")),
                document.getString("role"),
                document.getString("topic"),
                document.getString("status"),
                document.getInteger("progress", 0),
                document.getString("extractedText"),
                toJson(document.get("result"), objectMapper),
                document.getString("error"),
                dateTimeOrObjectId(document, "createdAt"),
                dateTime(document, "updatedAt")
        );
    }

    private static LegacyOption toOption(Document document) {
        return new LegacyOption(document.getString("letter"), document.getString("text"));
    }

    private static String objectId(Document document) {
        Object id = document.get("_id");
        if (id instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return id == null ? null : id.toString();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof org.bson.types.Decimal128 decimal) {
            return decimal.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String toJson(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar result de generation_job", e);
        }
    }

    /**
     * Los documentos de producción no tienen createdAt (se generaron antes de
     * activar la auditoría de Mongo). El timestamp del ObjectId es la única
     * fuente confiable y no se puede inventar con LocalDateTime.now().
     */
    private static LocalDateTime dateTimeOrObjectId(Document document, String field) {
        LocalDateTime value = dateTime(document, field);
        if (value != null) {
            return value;
        }
        Object id = document.get("_id");
        if (id instanceof ObjectId objectId) {
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(objectId.getTimestamp()), ZoneOffset.UTC);
        }
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime dateTime(Document document, String field) {
        Date date = document.getDate(field);
        return date == null
                ? null
                : LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }
}
