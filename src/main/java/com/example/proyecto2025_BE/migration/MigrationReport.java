package com.example.proyecto2025_BE.migration;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado del preflight y de la ejecución. Es mutable porque se completa
 * durante el recorrido por lotes.
 */
@Getter
@Setter
public class MigrationReport {

    private int legacyQuestions;
    private int uniqueQuestions;
    private int duplicateQuestions;
    private int legacyFeedback;
    private int legacyJobs;

    private int distinctTopics;
    private int mergedTopics;
    private int insertedTopics;
    private int existingTopics;

    private int expectedOptions;
    private int insertedQuestions;
    private int existingQuestions;
    private int insertedOptions;
    private int skippedOptions;

    private int answersToUpdate;
    private int updatedAnswers;
    private int unmappedAnswers;

    private int insertedFeedback;
    private int insertedJobs;
    private int orphanUsers;
    private int invalidQuestions;

    private final List<String> issues = new ArrayList<>();

    public void addIssue(String issue) {
        if (issues.size() < 100) {
            issues.add(issue);
        }
    }

    public int criticalIssues() {
        return orphanUsers + invalidQuestions + unmappedAnswers;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Legacy: questions=").append(legacyQuestions)
                .append(" unique=").append(uniqueQuestions)
                .append(" duplicateEnunciados=").append(duplicateQuestions)
                .append(" feedback=").append(legacyFeedback)
                .append(" jobs=").append(legacyJobs).append('\n');
        sb.append("Topics: distinct=").append(distinctTopics)
                .append(" merged=").append(mergedTopics)
                .append(" inserted=").append(insertedTopics)
                .append(" existing=").append(existingTopics).append('\n');
        sb.append("Questions: inserted=").append(insertedQuestions)
                .append(" existing=").append(existingQuestions)
                .append(" expectedOptions=").append(expectedOptions).append('\n');
        sb.append("Options: inserted=").append(insertedOptions)
                .append(" skipped=").append(skippedOptions).append('\n');
        sb.append("Answers: toUpdate=").append(answersToUpdate)
                .append(" updated=").append(updatedAnswers)
                .append(" unmapped=").append(unmappedAnswers).append('\n');
        sb.append("Feedback inserted=").append(insertedFeedback)
                .append(" jobs inserted=").append(insertedJobs).append('\n');
        sb.append("Orphan users=").append(orphanUsers)
                .append(" invalid questions=").append(invalidQuestions)
                .append(" issues=").append(issues.size());

        if (!issues.isEmpty()) {
            sb.append('\n').append("Primeros issues:");
            issues.forEach(issue -> sb.append('\n').append("  - ").append(issue));
        }
        return sb.toString();
    }
}
