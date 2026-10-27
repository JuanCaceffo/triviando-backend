-- V3: cierre de la migración de contenido.
--
-- Se ejecuta DESPUÉS del backfill. El perfil `migrate` corre con
-- spring.flyway.target=2 para que esta migración no se adelante.
--
-- Requiere que answer.question_id_new ya esté completo (lo garantiza el
-- backfill). Si quedara algún NULL, el ALTER ... NOT NULL falla y la app no
-- arranca: es una guarda deliberada.

-- Las FKs de answer y password_reset_tokens fueron creadas por Hibernate en
-- producción (nombres generados), mientras que en una base nueva las crea V1.
-- Se dropean por columna, sin depender del nombre.
DROP PROCEDURE IF EXISTS drop_fk_for_column;

DELIMITER //
CREATE PROCEDURE drop_fk_for_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    DECLARE v_fk VARCHAR(64);

    SELECT CONSTRAINT_NAME INTO v_fk
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column
      AND REFERENCED_TABLE_NAME IS NOT NULL
    LIMIT 1;

    IF v_fk IS NOT NULL THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP FOREIGN KEY `', v_fk, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL drop_fk_for_column('answer', 'user_id');
CALL drop_fk_for_column('password_reset_tokens', 'user_id');
DROP PROCEDURE drop_fk_for_column;

-- answer: reemplazar el ObjectId string por la FK a questions.
ALTER TABLE answer MODIFY user_id BIGINT NOT NULL;
ALTER TABLE answer ADD UNIQUE KEY uk_answer_user_question (user_id, question_id_new);
ALTER TABLE answer DROP COLUMN question_id;
ALTER TABLE answer CHANGE COLUMN question_id_new question_id BIGINT NOT NULL;
ALTER TABLE answer
    ADD CONSTRAINT fk_answer_question
        FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE;
ALTER TABLE answer
    ADD CONSTRAINT fk_answer_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE answer ADD KEY idx_answer_user_date (user_id, response_date);

-- Borrar un usuario debe borrar también sus tokens de recuperación.
ALTER TABLE password_reset_tokens
    ADD CONSTRAINT fk_password_reset_tokens_user_cascade
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Identidad única a nivel de base (la app ya lo validaba en código).
ALTER TABLE users ADD UNIQUE KEY uk_users_email (email);
ALTER TABLE users ADD UNIQUE KEY uk_users_username (username);

-- Tabla muerta: la entidad Option ahora vive en question_options.
DROP TABLE IF EXISTS options;
