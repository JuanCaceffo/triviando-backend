-- Verificación posterior a V3. Ejecutar sobre la base `flashcards`:
--   docker exec -i mysql-db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" flashcards' \
--     < scripts/migration/verify-post-cutover.sql
--
-- Todos los conteos marcados como "0" deben ser cero.

SELECT 'users' AS metrica, COUNT(*) AS valor FROM users
UNION ALL SELECT 'answers', COUNT(*) FROM answer
UNION ALL SELECT 'topics', COUNT(*) FROM topics
UNION ALL SELECT 'questions', COUNT(*) FROM questions
UNION ALL SELECT 'question_options', COUNT(*) FROM question_options
UNION ALL SELECT 'legacy_id_map', COUNT(*) FROM legacy_id_map
UNION ALL SELECT 'feedback', COUNT(*) FROM feedback
UNION ALL SELECT 'generation_jobs', COUNT(*) FROM generation_jobs;

-- Deben ser 0:
SELECT 'questions_sin_4_opciones' AS metrica, COUNT(*) AS valor
FROM (SELECT question_id FROM question_options GROUP BY question_id HAVING COUNT(*) <> 4) x;

SELECT 'questions_sin_1_correcta' AS metrica, COUNT(*) AS valor
FROM (SELECT question_id FROM question_options GROUP BY question_id HAVING SUM(is_correct) <> 1) x;

SELECT 'answers_sin_pregunta' AS metrica, COUNT(*) AS valor
FROM answer a LEFT JOIN questions q ON q.id = a.question_id WHERE q.id IS NULL;

SELECT 'answers_duplicadas' AS metrica, COUNT(*) AS valor
FROM (SELECT user_id, question_id FROM answer GROUP BY user_id, question_id HAVING COUNT(*) > 1) x;

SELECT 'topics_sin_preguntas' AS metrica, COUNT(*) AS valor
FROM topics t LEFT JOIN questions q ON q.topic_id = t.id WHERE q.id IS NULL;

-- Paridad esperada: los scores no cambian con la migración.
SELECT user_id, SUM(score) AS score_total
FROM answer
GROUP BY user_id
ORDER BY user_id;
