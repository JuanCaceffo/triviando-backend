package com.example.proyecto2025_BE.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Migra el contenido de MongoDB a las tablas nuevas de MySQL.
 *
 * Es idempotente: se puede ejecutar varias veces y no duplica tópicos,
 * preguntas, opciones ni respuestas. El modo dry-run hace el mismo análisis
 * pero no escribe.
 */
@Slf4j
@Service
@Profile("migrate")
public class ContentMigrationService {

    private static final Set<String> DIFFICULTIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> ROLES = Set.of("FREE", "PREMIUM");
    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");
    private static final Set<String> FEEDBACK_TYPES = Set.of("POSITIVE", "NEGATIVE");

    private final JdbcTemplate jdbc;
    private final LegacyContentReader reader;
    private final MigrationProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ContentMigrationService(
            JdbcTemplate jdbc,
            LegacyContentReader reader,
            MigrationProperties properties,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.reader = reader;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MigrationReport run() {
        LegacyContent content = reader.read();
        MigrationReport report = analyze(content);
        log.info("Preflight de migración:\n{}", report.summary());

        if (properties.isDryRun()) {
            log.info("Dry-run: no se escribió nada en MySQL");
            return report;
        }

        assertCanMigrate(content, report);

        Map<String, Long> legacyMap = loadLegacyMap();
        migrateQuestions(content.questions(), legacyMap, report);
        updateAnswers(report);
        migrateFeedback(content.feedback(), legacyMap, report);
        migrateJobs(content.jobs(), report);

        return report;
    }

    private MigrationReport analyze(LegacyContent content) {
        MigrationReport report = new MigrationReport();
        report.setLegacyQuestions(content.questions().size());
        report.setLegacyFeedback(content.feedback().size());
        report.setLegacyJobs(content.jobs().size());

        Set<Long> userIds = new HashSet<>(jdbc.queryForList("SELECT id FROM users", Long.class));
        Map<TopicKey, Set<String>> topicNames = new HashMap<>();
        Map<QuestionKey, List<String>> questionGroups = new HashMap<>();
        Set<String> legacyQuestionIds = new HashSet<>();

        for (LegacyQuestion question : content.questions()) {
            if (question.objectId() != null) {
                legacyQuestionIds.add(question.objectId());
            }

            if (question.userId() == null || !userIds.contains(question.userId())) {
                report.setOrphanUsers(report.getOrphanUsers() + 1);
                report.addIssue("Pregunta " + question.objectId() + " apunta a userId inexistente: " + question.userId());
            }

            List<String> invalidReasons = validateQuestion(question);
            if (!invalidReasons.isEmpty()) {
                report.setInvalidQuestions(report.getInvalidQuestions() + 1);
                report.addIssue("Pregunta " + question.objectId() + ": " + String.join(", ", invalidReasons));
            }

            String displayName = MigrationNormalizer.displayName(question.topic());
            String normalized = MigrationNormalizer.normalizedTopic(question.topic());
            TopicKey key = new TopicKey(question.userId(), normalized);
            topicNames.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(displayName);

            QuestionKey questionKey = new QuestionKey(
                    question.userId(),
                    normalized,
                    MigrationNormalizer.enunciadoHash(question.enunciado()));
            questionGroups.computeIfAbsent(questionKey, ignored -> new ArrayList<>())
                    .add(question.objectId());
        }

        report.setDistinctTopics(topicNames.size());
        report.setMergedTopics((int) topicNames.values().stream()
                .filter(names -> names.size() > 1)
                .count());
        report.setUniqueQuestions(questionGroups.size());
        report.setDuplicateQuestions(Math.max(0, content.questions().size() - questionGroups.size()));
        report.setExpectedOptions(content.questions().size() * 4);
        questionGroups.values().stream()
                .filter(ids -> ids.size() > 1)
                .limit(10)
                .forEach(ids -> report.addIssue(
                        "Enunciado duplicado, se conservan todos (" + ids.size() + " ObjectIds): " + ids));

        List<String> answeredQuestionIds = jdbc.queryForList(
                "SELECT DISTINCT question_id FROM answer WHERE question_id IS NOT NULL",
                String.class);
        report.setAnswersToUpdate(answeredQuestionIds.size());
        report.setUnmappedAnswers((int) answeredQuestionIds.stream()
                .filter(id -> !legacyQuestionIds.contains(id))
                .count());

        if (report.getUnmappedAnswers() > 0) {
            report.addIssue(report.getUnmappedAnswers()
                    + " respuestas apuntan a preguntas que no existen en Mongo");
        }

        return report;
    }

    private List<String> validateQuestion(LegacyQuestion question) {
        List<String> reasons = new ArrayList<>();

        if (isBlank(question.objectId())) {
            reasons.add("sin _id");
        }
        if (isBlank(question.topic())) {
            reasons.add("sin topico");
        } else if (MigrationNormalizer.displayName(question.topic()).length() > MigrationNormalizer.MAX_TOPIC_LENGTH) {
            reasons.add("topico supera " + MigrationNormalizer.MAX_TOPIC_LENGTH + " caracteres");
        }
        if (isBlank(question.enunciado())) {
            reasons.add("sin enunciado");
        }
        if (question.createdAt() == null) {
            reasons.add("sin createdAt ni ObjectId");
        }
        if (!DIFFICULTIES.contains(question.difficulty())) {
            reasons.add("difficulty inválida: " + question.difficulty());
        }
        if (question.options() == null || question.options().isEmpty()) {
            reasons.add("sin opciones");
            return reasons;
        }

        Set<String> letters = new HashSet<>();
        for (LegacyOption option : question.options()) {
            if (isBlank(option.letter()) || isBlank(option.text())) {
                reasons.add("opción sin letra o texto");
                continue;
            }
            if (!letters.add(option.letter().toUpperCase())) {
                reasons.add("letra de opción repetida: " + option.letter());
            }
        }

        if (isBlank(question.correctLetter())
                || letters.stream().noneMatch(letter -> letter.equalsIgnoreCase(question.correctLetter()))) {
            reasons.add("correctOption no coincide con ninguna opción");
        }

        return reasons;
    }

    private void assertCanMigrate(LegacyContent content, MigrationReport report) {
        if (content.questions().isEmpty()) {
            throw new MigrationAbortedException("No hay preguntas en Mongo: se aborta para no vaciar contenido");
        }
        if (properties.isFailOnOrphans() && report.criticalIssues() > 0) {
            throw new MigrationAbortedException(
                    "Preflight con " + report.criticalIssues()
                            + " problemas críticos; corregir o ejecutar con --app.migration.fail-on-orphans=false");
        }
    }

    private void migrateQuestions(
            List<LegacyQuestion> questions,
            Map<String, Long> legacyMap,
            MigrationReport report) {

        List<LegacyQuestion> sorted = questions.stream()
                .sorted(Comparator.comparing(LegacyQuestion::createdAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LegacyQuestion::objectId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<TopicKey, Long> topicCache = new HashMap<>();
        int batchSize = properties.getBatchSize() > 0 ? properties.getBatchSize() : 100;

        for (List<LegacyQuestion> batch : partition(sorted, batchSize)) {
            transactionTemplate.executeWithoutResult(status -> {
                for (LegacyQuestion question : batch) {
                    migrateQuestion(question, legacyMap, topicCache, report);
                }
            });
        }
    }

    private void migrateQuestion(
            LegacyQuestion question,
            Map<String, Long> legacyMap,
            Map<TopicKey, Long> topicCache,
            MigrationReport report) {

        Long topicId = upsertTopic(question, topicCache, report);
        Long questionId = legacyMap.get(question.objectId());

        if (questionId == null) {
            questionId = insertQuestion(question, topicId, report);
            legacyMap.put(question.objectId(), questionId);
        } else {
            report.setExistingQuestions(report.getExistingQuestions() + 1);
        }

        jdbc.update("INSERT IGNORE INTO legacy_id_map (object_id, question_id) VALUES (?, ?)",
                question.objectId(), questionId);
        upsertOptions(questionId, question, report);
    }

    private Long upsertTopic(
            LegacyQuestion question,
            Map<TopicKey, Long> topicCache,
            MigrationReport report) {

        String displayName = MigrationNormalizer.displayName(question.topic());
        String normalized = MigrationNormalizer.normalizedTopic(question.topic());
        TopicKey key = new TopicKey(question.userId(), normalized);

        Long cached = topicCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<Long> existing = jdbc.queryForList(
                "SELECT id FROM topics WHERE user_id = ? AND name_normalized = ?",
                Long.class, key.userId(), key.normalized());
        if (!existing.isEmpty()) {
            report.setExistingTopics(report.getExistingTopics() + 1);
            topicCache.put(key, existing.getFirst());
            return existing.getFirst();
        }

        try {
            Long id = insertTopic(question, displayName, normalized);
            report.setInsertedTopics(report.getInsertedTopics() + 1);
            topicCache.put(key, id);
            return id;
        } catch (DuplicateKeyException duplicate) {
            Long id = jdbc.queryForList(
                            "SELECT id FROM topics WHERE user_id = ? AND name_normalized = ?",
                            Long.class, key.userId(), key.normalized())
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Tópico duplicado pero no encontrado: " + key, duplicate));
            report.setExistingTopics(report.getExistingTopics() + 1);
            topicCache.put(key, id);
            return id;
        }
    }

    private Long insertTopic(LegacyQuestion question, String displayName, String normalized) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO topics (user_id, name, name_normalized, emoji, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, question.userId());
            ps.setString(2, displayName);
            ps.setString(3, normalized);
            ps.setString(4, blankToNull(question.emoji()));
            ps.setTimestamp(5, Timestamp.valueOf(question.createdAt()));
            return ps;
        }, keyHolder);
        return requiredKey(keyHolder, "topics");
    }

    /**
     * Inserta una pregunta por cada ObjectId. La idempotencia se garantiza con
     * legacy_id_map, no con el hash: dos documentos con el mismo enunciado son
     * dos filas y se migran completos.
     */
    private Long insertQuestion(
            LegacyQuestion question,
            Long topicId,
            MigrationReport report) {

        String hash = MigrationNormalizer.enunciadoHash(question.enunciado());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO questions "
                            + "(topic_id, enunciado, enunciado_hash, explicacion, difficulty, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, topicId);
            ps.setString(2, question.enunciado());
            ps.setString(3, hash);
            ps.setString(4, blankToNull(question.explicacion()));
            ps.setString(5, question.difficulty());
            ps.setTimestamp(6, Timestamp.valueOf(question.createdAt()));
            return ps;
        }, keyHolder);

        report.setInsertedQuestions(report.getInsertedQuestions() + 1);
        return requiredKey(keyHolder, "questions");
    }

    private void upsertOptions(Long questionId, LegacyQuestion question, MigrationReport report) {
        // INSERT IGNORE, no upsert: si dos ObjectId colapsan al mismo enunciado,
        // el primero (el más antiguo) define las opciones y no se pisan.
        String sql = """
                INSERT IGNORE INTO question_options
                    (question_id, letter, text, is_correct, position_order)
                VALUES (?, ?, ?, ?, ?)
                """;

        for (int position = 0; position < question.options().size(); position++) {
            LegacyOption option = question.options().get(position);
            boolean correct = option.letter().equalsIgnoreCase(question.correctLetter());
            int rows = jdbc.update(sql,
                    questionId,
                    option.letter().toUpperCase(),
                    option.text(),
                    correct,
                    position);
            if (rows == 1) {
                report.setInsertedOptions(report.getInsertedOptions() + 1);
            } else {
                report.setSkippedOptions(report.getSkippedOptions() + 1);
            }
        }
    }

    private Map<String, Long> loadLegacyMap() {
        Map<String, Long> map = new HashMap<>();
        jdbc.query("SELECT object_id, question_id FROM legacy_id_map", rs -> {
            map.put(rs.getString("object_id"), rs.getLong("question_id"));
        });
        return map;
    }

    private void updateAnswers(MigrationReport report) {
        int updated = jdbc.update("""
                UPDATE answer a
                JOIN legacy_id_map m ON a.question_id = m.object_id
                SET a.question_id_new = m.question_id
                """);
        report.setUpdatedAnswers(updated);

        Integer unmapped = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM answer a
                LEFT JOIN legacy_id_map m ON a.question_id = m.object_id
                WHERE m.object_id IS NULL
                """, Integer.class);
        report.setUnmappedAnswers(unmapped == null ? 0 : unmapped);
    }

    private void migrateFeedback(
            List<LegacyFeedback> feedback,
            Map<String, Long> legacyMap,
            MigrationReport report) {

        for (LegacyFeedback item : feedback) {
            if (item.userId() == null || !FEEDBACK_TYPES.contains(item.feedbackType())) {
                report.addIssue("Feedback " + item.objectId() + " con datos inválidos");
                continue;
            }
            if (!userExists(item.userId())) {
                report.addIssue("Feedback " + item.objectId() + " apunta a userId inexistente: " + item.userId());
                continue;
            }

            Long questionId = legacyMap.get(item.questionId());
            if (questionId == null) {
                report.addIssue("Feedback " + item.objectId() + " apunta a pregunta desconocida: " + item.questionId());
                continue;
            }

            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM feedback
                    WHERE user_id = ?
                      AND question_id = ?
                      AND feedback_type = ?
                      AND COALESCE(description, '') = COALESCE(?, '')
                    """, Integer.class, item.userId(), questionId, item.feedbackType(), item.description());

            if (exists != null && exists > 0) {
                continue;
            }

            jdbc.update("""
                    INSERT INTO feedback (user_id, question_id, feedback_type, description, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    item.userId(),
                    questionId,
                    item.feedbackType(),
                    item.description(),
                    Timestamp.valueOf(item.createdAt() == null
                            ? LocalDateTime.now(ZoneOffset.UTC)
                            : item.createdAt()));
            report.setInsertedFeedback(report.getInsertedFeedback() + 1);
        }
    }

    private void migrateJobs(List<LegacyGenerationJob> jobs, MigrationReport report) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minusHours(properties.getJobsWindowHours());

        for (LegacyGenerationJob job : jobs) {
            if (job.createdAt() == null || job.createdAt().isBefore(cutoff)) {
                continue;
            }
            if (job.userId() == null
                    || !ROLES.contains(job.role())
                    || !STATUSES.contains(job.status())) {
                report.addIssue("Job " + job.objectId() + " con datos inválidos");
                continue;
            }
            if (!userExists(job.userId())) {
                report.addIssue("Job " + job.objectId() + " apunta a userId inexistente: " + job.userId());
                continue;
            }

            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM generation_jobs
                    WHERE user_id = ?
                      AND created_at = ?
                      AND status = ?
                      AND COALESCE(topic, '') = COALESCE(?, '')
                    """, Integer.class, job.userId(), Timestamp.valueOf(job.createdAt()),
                    job.status(), job.topic());

            if (exists != null && exists > 0) {
                continue;
            }

            jdbc.update("""
                    INSERT INTO generation_jobs
                        (user_id, role, topic, status, progress, extracted_text, result, error, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    job.userId(),
                    job.role(),
                    job.topic(),
                    job.status(),
                    job.progress(),
                    job.extractedText(),
                    job.resultJson(),
                    job.error(),
                    Timestamp.valueOf(job.createdAt()),
                    job.updatedAt() == null ? null : Timestamp.valueOf(job.updatedAt()));
            report.setInsertedJobs(report.getInsertedJobs() + 1);
        }
    }

    private boolean userExists(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    private static Long requiredKey(KeyHolder keyHolder, String table) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se obtuvo la PK generada de " + table);
        }
        return key.longValue();
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            batches.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return batches;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private record TopicKey(Long userId, String normalized) {
    }

    private record QuestionKey(Long userId, String normalizedTopic, String enunciadoHash) {
    }
}
