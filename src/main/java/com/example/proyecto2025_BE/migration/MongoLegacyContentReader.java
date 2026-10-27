package com.example.proyecto2025_BE.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lee el contenido legacy desde MongoDB usando documentos crudos.
 * Solo existe en el perfil {@code migrate}.
 */
@Component
@Profile("migrate")
@RequiredArgsConstructor
public class MongoLegacyContentReader implements LegacyContentReader {

    private final MongoTemplate mongoTemplate;
    private final MigrationProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public LegacyContent read() {
        List<LegacyQuestion> questions = mongoTemplate
                .findAll(Document.class, properties.getQuestionsCollection())
                .stream()
                .map(LegacyDocumentMapper::toQuestion)
                .toList();

        List<LegacyFeedback> feedback = mongoTemplate
                .findAll(Document.class, properties.getFeedbackCollection())
                .stream()
                .map(LegacyDocumentMapper::toFeedback)
                .toList();

        List<LegacyGenerationJob> jobs = mongoTemplate
                .findAll(Document.class, properties.getJobsCollection())
                .stream()
                .map(document -> LegacyDocumentMapper.toJob(document, objectMapper))
                .toList();

        return new LegacyContent(questions, feedback, jobs);
    }
}
