-- V1: baseline del esquema MySQL existente en producción (MySQL 8.0.46).
--
-- En producción (esquema no vacío) Flyway crea el baseline en V1 y NO ejecuta
-- este archivo: solo aplica V2 en adelante.
-- En una base vacía (dev/test MySQL) este archivo crea el esquema original.
--
-- No editar: representa el estado anterior a la migración a MySQL único.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account TINYINT DEFAULT NULL,
    birth_date DATE DEFAULT NULL,
    country_code VARCHAR(255) DEFAULT NULL,
    email VARCHAR(255) DEFAULT NULL,
    google_account BIT(1) NOT NULL,
    join_date DATETIME(6) DEFAULT NULL,
    last_name VARCHAR(255) DEFAULT NULL,
    name VARCHAR(255) DEFAULT NULL,
    password VARCHAR(255) DEFAULT NULL,
    phone_number VARCHAR(255) DEFAULT NULL,
    privileges VARBINARY(255) DEFAULT NULL,
    racha_actual INT DEFAULT NULL,
    ultima_actividad DATE DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    username VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    error_reason VARCHAR(255) DEFAULT NULL,
    milliseconds_spent BIGINT NOT NULL,
    option_selected ENUM ('A', 'B', 'C', 'D', 'E') DEFAULT NULL,
    question_id VARCHAR(255) DEFAULT NULL,
    response_date DATE DEFAULT NULL,
    score DECIMAL(38, 2) DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_answer_user (user_id),
    CONSTRAINT fk_answer_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    letter ENUM ('A', 'B', 'C', 'D', 'E') DEFAULT NULL,
    text VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempts INT NOT NULL,
    code_hash VARCHAR(255) DEFAULT NULL,
    expires_at DATETIME(6) DEFAULT NULL,
    last_sent_at DATETIME(6) DEFAULT NULL,
    reset_token VARCHAR(255) DEFAULT NULL,
    reset_token_expires_at DATETIME(6) DEFAULT NULL,
    used BIT(1) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
