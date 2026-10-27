-- V2: esquema aditivo para migrar el contenido de MongoDB a MySQL.
--
-- Es seguro aplicar esta migración con el código anterior en ejecución:
-- solo crea tablas nuevas y una columna nullable en answer. El código nuevo
-- que usa estas tablas se activa recién después del backfill y de V3.
--
-- Orden del proceso:
--   1. V2 (este archivo) -> tablas nuevas vacías + answer.question_id_new
--   2. Backfill (perfil migrate) -> llena las tablas y question_id_new
--   3. V3 -> elimina la columna vieja, renombra, agrega FKs y constraints
--
-- El tópico se normaliza por usuario (trim + minúsculas + sin acentos +
-- espacios colapsados). El enunciado se indexa por SHA-256 porque TEXT no
-- puede formar parte de un índice único.

CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL,
    emoji VARCHAR(16) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topics_user_name (user_id, name_normalized),
    CONSTRAINT fk_topics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    enunciado TEXT NOT NULL,
    enunciado_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    explicacion TEXT DEFAULT NULL,
    difficulty ENUM ('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- Índice, no UNIQUE: la data histórica tiene enunciados repetidos y se
    -- preservan todos. El dedup de contenido nuevo se hace en generación.
    KEY idx_questions_topic_hash (topic_id, enunciado_hash),
    CONSTRAINT fk_questions_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    letter ENUM ('A', 'B', 'C', 'D', 'E') NOT NULL,
    text TEXT NOT NULL,
    is_correct BIT(1) NOT NULL DEFAULT b'0',
    position_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_options_letter (question_id, letter),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    feedback_type ENUM ('POSITIVE', 'NEGATIVE') NOT NULL,
    description TEXT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_feedback_user (user_id),
    KEY idx_feedback_question (question_id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE generation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role ENUM ('FREE', 'PREMIUM') NOT NULL,
    topic VARCHAR(120) DEFAULT NULL,
    status ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    extracted_text LONGTEXT DEFAULT NULL,
    result JSON DEFAULT NULL,
    error TEXT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_generation_jobs_user_status (user_id, status),
    KEY idx_generation_jobs_created (created_at),
    CONSTRAINT fk_generation_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Mapa permanente de ObjectId de Mongo -> PK de MySQL. Permite re-ejecutar el
-- backfill y auditar la correspondencia después del corte.
CREATE TABLE legacy_id_map (
    object_id CHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (object_id),
    -- No es UNIQUE: varios ObjectId pueden representar el mismo enunciado
    -- normalizado y deben mapear a la misma pregunta canónica.
    KEY idx_legacy_id_map_question (question_id),
    CONSTRAINT fk_legacy_id_map_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Columna temporal para no reescribir answer.question_id (VARCHAR) hasta que
-- el backfill haya mapeado todos los ObjectId.
ALTER TABLE answer
    ADD COLUMN question_id_new BIGINT DEFAULT NULL;
