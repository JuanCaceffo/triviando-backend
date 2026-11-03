-- V4: salas de trivia en vivo.
--
-- Todo el contenido de una sala es efímero y vive en tablas propias. No se
-- relaciona con topics/questions/answer para no afectar la biblioteca del
-- usuario, su score, su racha ni el ranking global.

CREATE TABLE rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(8) NOT NULL,
    host_user_id BIGINT NOT NULL,
    status ENUM ('PREPARING', 'LOBBY', 'IN_PROGRESS', 'FINISHED', 'CLOSED', 'FAILED') NOT NULL,
    source ENUM ('TOPIC', 'FILE') NOT NULL,
    topic VARCHAR(120) NOT NULL,
    question_count INT NOT NULL DEFAULT 0,
    current_question_index INT NOT NULL DEFAULT -1,
    question_started_at DATETIME(6) DEFAULT NULL,
    question_deadline_at DATETIME(6) DEFAULT NULL,
    reveal_until DATETIME(6) DEFAULT NULL,
    close_reason ENUM ('HOST_LEFT', 'HOST_CLOSED', 'EXPIRED', 'GENERATION_FAILED', 'EMPTY', 'INTERRUPTED') DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) DEFAULT NULL,
    finished_at DATETIME(6) DEFAULT NULL,
    expires_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_code (code),
    KEY idx_rooms_status_expires (status, expires_at),
    KEY idx_rooms_host_status (host_user_id, status),
    KEY idx_rooms_expires (expires_at),
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room_generations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    status ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    extracted_text LONGTEXT DEFAULT NULL,
    error TEXT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_generations_room (room_id),
    CONSTRAINT fk_room_generations_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    total_response_ms BIGINT NOT NULL DEFAULT 0,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) DEFAULT NULL,
    last_seen_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_members_room_user (room_id, user_id),
    KEY idx_room_members_user (user_id),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    position INT NOT NULL,
    enunciado TEXT NOT NULL,
    explicacion TEXT DEFAULT NULL,
    difficulty ENUM ('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_questions_room_position (room_id, position),
    CONSTRAINT fk_room_questions_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room_question_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    letter ENUM ('A', 'B', 'C', 'D', 'E') NOT NULL,
    text TEXT NOT NULL,
    is_correct BIT(1) NOT NULL DEFAULT b'0',
    position_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_question_options_letter (question_id, letter),
    CONSTRAINT fk_room_question_options_question FOREIGN KEY (question_id)
        REFERENCES room_questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    option_selected ENUM ('A', 'B', 'C', 'D', 'E') NOT NULL,
    milliseconds_spent BIGINT NOT NULL,
    is_correct BIT(1) NOT NULL,
    points INT NOT NULL,
    answered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_answers_question_user (question_id, user_id),
    KEY idx_room_answers_room_user (room_id, user_id),
    CONSTRAINT fk_room_answers_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_answers_question FOREIGN KEY (question_id)
        REFERENCES room_questions (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_answers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
