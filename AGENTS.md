# AGENTS.md

Guía para agentes de IA que trabajan en este repositorio.

## Visión general

**Triviando** es una API REST que crea y regenera quizzes (preguntas) mediante IA. También gestiona usuarios, autenticación (JWT + Google), suscripciones FREE/PREMIUM (MercadoPago), rankings, estadísticas, rachas y **salas de trivia en vivo (SSE)**.

Proyecto académico (UNSAM, grupo "Nullpointer"). El frontend vive en otro repo (Vercel). Detalles de proceso (JIRA, Git Flow completo) están en `README.md`.

## Stack y arquitectura

- **Java 21 + Spring Boot 3.4.3** (Maven, wrapper `mvnw`), puerto `8080`, context path **`/api/v1`**
- **IA**: LangChain4j (`AiServices`) con OpenAI `gpt-6-luna` (`reasoningEffort: medium` en generación, `none` en el resumidor de documentos, JSON schema estricto, chat memory de 10 mensajes)
- **Auth**: JWT (jjwt) + BCrypt + Google Sign-In; **Pagos**: MercadoPago; **Docs**: Swagger (`/api/v1/swagger-ui`)
- **Perfiles**: `dev` (default), `prod`, `test` (`src/main/resources/application*.yml`)
- **Despliegue**: `Dockerfile` + `docker-compose.yml` (app + mysql)

### Paquetes base: `src/main/java/com/example/proyecto2025_BE/`

| Paquete | Contenido |
|---|---|
| `controller` | Endpoints REST (Auth, User, Question, Answer, Subscription) |
| `service` | Lógica de negocio (`IQuestion`, `LLMApiClientService`, `UserService`, etc.) |
| `repository` | Repositorios Spring Data JPA |
| `model` | Entidades, DTOs (`model/dto`), prompters (`model/prompter`), MercadoPago (`model/mp`), rachas (`model/racha`) |
| `configuration` | Beans: `LLMConfig`, `MPConfig`, `MySQLConfig`, Swagger, CORS, serializadores |
| `security` | `WebSecurityConfig`, `JwtUtil`, `AuthTokenFilter`, `UserContext` |
| `views` | DTOs de request/response de la API + esquemas Swagger |
| `constants` | Prompts del LLM (`LLM.java`), mensajes de error, patrones |
| `exceptions` | `GlobalExceptionHandler` |

## Base de datos: MySQL único

- **MySQL 8 + JPA/Hibernate + Flyway** (`ddl-auto: validate`; el esquema lo gobiernan las migraciones de `src/main/resources/db/migration`):
  - `User` (`users`), `Answer` (`answer`), `PasswordResetToken` (`password_reset_tokens`)
  - `Topic` (`topics`), `Pregunta` (`questions`), `Option` (`question_options`)
  - `Feedback` (`feedback`), `GenerationJob` (`generation_jobs`)
  - Salas: `rooms`, `room_members`, `room_questions`, `room_question_options`, `room_answers`, `room_generations`
  - `legacy_id_map` — auditoría histórica ObjectId → PK (no la usa el runtime)
- **Jobs**: `generation_jobs` en MySQL; `GenerationJobCleanupService` borra los jobs de más de 24 h cada hora.
- **Tests**: H2 en memoria para unitarios/integración (`application-test.yml`, Flyway deshabilitado); las migraciones y el esquema real se validan contra MySQL.

Consecuencia: todo cambio de esquema requiere una migración Flyway nueva (`V5__...sql` en adelante; `V4__cooperative_rooms.sql` es la de salas) y actualizar la entidad; nunca `ddl-auto: update`.

## Endpoints clave (prefijo `/api/v1`)

### Auth y usuarios
- `POST /users` — registro
- `POST /users/login` — login (devuelve JWT)
- `POST /auth/google` — login con Google ID token
- `POST /auth/forgot-password` → `POST /auth/verify-reset-code` → `POST /auth/reset-password` — recuperación vía e-mail (SMTP)
- `GET /users/{id}`, `DELETE /users/{id}`, `GET /users/statistics/{usuarioId}`, `GET /users/score/{userId}`
- `GET /users/ranking`, `GET /users/ranking/{userId}`, `GET /users/ranking-semanal[/{userId}]`, `GET /users/racha/{userId}`

### Preguntas (quiz)
- `POST /preguntas/generate` — **genera preguntas con IA** (body: `Prompter` con campo `promptType`)
- `GET /preguntas?userId=&topico=` — preguntas no respondidas (opcionalmente por tópico)
- `GET /preguntas/{id}`, `GET /preguntas/topicos/{userId}` — conteo por tópico
- `POST /preguntas/send-feedback` — feedback sobre una pregunta
- `POST /preguntas/document/generate` — **genera un tópico desde un archivo** (multipart: `file` + `topic`, PDF/DOCX/TXT/MD hasta 10 MB). Devuelve `202` con `jobId`
- `GET /preguntas/document/jobs/{jobId}` — estado/progreso del job (`PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`) y resultado

### Salas en vivo
- `POST /rooms` — crea una sala desde un tema (solo PREMIUM; `201 {code,status}`)
- `POST /rooms/document` — crea una sala desde un archivo (multipart `topic` + `file`, solo PREMIUM)
- `GET /rooms/active` — sala visible del usuario (o `204`)
- `POST /rooms/{code}/join` — une/reconecta; idempotente; valida estado, capacidad y una sala por jugador
- `GET /rooms/{code}` — snapshot de la sala (solo miembros)
- `GET /rooms/{code}/events` — stream SSE (`text/event-stream`) con snapshots y reacciones
- `POST /rooms/{code}/start` — inicia la partida (solo anfitrión, mínimo 2 jugadores)
- `POST /rooms/{code}/answers` — responde; devuelve solo confirmación, nunca el acierto ni los puntos
- `POST /rooms/{code}/reactions` — reacción emoji efímera con whitelist y cooldown
- `POST /rooms/{code}/leave` — sale; si es anfitrión, cierra la sala
- `DELETE /rooms/{code}` — cierra la sala (solo anfitrión)

### Respuestas y suscripciones
- `POST /answers` — responde una pregunta
- `GET /suscriptions/plan` — devuelve precio, moneda y tipo de cobro del plan Premium (requiere JWT)
- `POST /suscriptions?email=` — crea preferencia de checkout MercadoPago
- `POST /suscriptions/payment` — webhook de notificación de pago

Endpoints abiertos (sin JWT) están listados en `WebSecurityConfig` (`security/`); todo el resto requiere token Bearer.

## Flujo de generación con IA

1. `POST /preguntas/generate` → `QuestionController.generate(Prompter)` → `LLMApiClientService.generate(prompter, role, userId)`
2. `model/prompter/Prompter` es polimórfico según el campo `promptType` del body:
   - **`topicPrompter`** (`TopicPrompter`): tópico nuevo, 10 preguntas, rechaza si el tópico ya existe, genera emoji con una 2da llamada al LLM
   - **`questionPrompter`** (`QuestionPrompter`): tópico existente, 5 preguntas, reutiliza el emoji
4. El prompt se construye con la plantilla de `Prompter` + system prompt en `constants/LLM.java`
5. `ModelCommunication` (interfaz construida en `configuration/LLMConfig` vía LangChain4j `AiServices`) devuelve un DTO `QuestionList` (`model/dto/llm`) → se mapea a `Pregunta` + `Option` → `QuestionService` guarda en MySQL (tópico único por usuario, opciones con `is_correct`)
6. El rol (FREE/PREMIUM, enum `Account`) y el userId llegan del JWT vía `security/UserContext`. Los usuarios FREE no tienen límite diario de temas ni de preguntas; la generación desde archivos es exclusiva de PREMIUM (403 para el resto).

### Generación desde archivo (asíncrona)

1. `POST /preguntas/document/generate` pasa por `GenerationRequestPolicy` (solo plan PREMIUM, tópico no duplicado, un job activo por usuario) y extrae el texto en memoria con `DocumentTextExtractionService` y las estrategias de `service/document` (PDFBox/POI/plano; validación, normalización y límites en `api.documents`).
2. `GenerationJobService` guarda un `GenerationJob` en MySQL (`PENDING`) y `DocumentGenerationWorker` lo encola (`@Async("documentGenerationExecutor")`, pool acotado en `AsyncConfig`).
3. Si el texto supera `api.documents.prompt-budget-chars`, `DocumentContextBuilder` hace map-reduce con `DocumentSummarizer` (LangChain4j, system prompt propio) muestreando bloques distribuidos por todo el documento.
4. Con el contexto resumido construye un `FilePrompter` mediante `FilePrompter.forDocument(...)` (extiende `TopicPrompter` y exige contexto) y reutiliza `LLMApiClientService.generate` (dedup, emoji, persistencia en `preguntas`).
5. El frontend consulta el job por polling (~2,5 s, timeout 5 min). `GenerationJobMapper` traduce entidad ↔ DTO; el texto extraído se borra del job al terminar y `GenerationJobCleanupService` elimina los jobs de más de 24 h.
6. PDFs escaneados no tienen OCR: se devuelve error de extracción.

## Salas de trivia en vivo

- **Aislamiento total de estadísticas**: las respuestas de sala viven en `room_answers`; nunca se escribe en `answer`, `topics` ni `questions`. Tampoco se llama a `AnswerService`, `UserService.update`, `actualizarRacha` ni `generateQuiz`. Un test de integración (`RoomNoStatsTest`) verifica que score, racha, ranking, estadísticas y conteos de contenido no cambian.
- **Creación**: solo PREMIUM (`UserContext.getContextRole()`); unirse no requiere Premium. La sala nace en `PREPARING` con código de 6 caracteres y el anfitrión ya es miembro. `RoomGenerationService` crea una fila en `room_generations` y dispara `RoomGenerationWorker` después del commit.
- **Generación**: `RoomGenerationProcessor` usa `LLMApiClientService.generateQuestions(...)` (método nuevo, sin persistir tópicos) con `RoomPrompter` o `RoomFilePrompter`. El resultado se mapea a `room_questions`/`room_question_options`; progreso y errores quedan en `room_generations`. Si falla, la sala pasa a `FAILED`.
- **Estados**: `PREPARING → LOBBY → IN_PROGRESS → FINISHED`; `CLOSED`/`FAILED` son terminales. `RoomLifecycleScheduler.tick()` cierra rondas vencidas, avanza reveals, expira salas y cierra si el anfitrión estuvo ausente más de `host-grace-seconds`. El scheduler se desactiva en tests (`api.rooms.scheduler-enabled: false`).
- **Rondas**: 10 preguntas, 30 s, cierre anticipado cuando todos los conectados respondieron, reveal de 8 s y avance automático. El servidor es la única autoridad de tiempo (`Clock`); la respuesta calcula `100 + bonus por velocidad` y guarda `is_correct`/`points` en `room_answers`, pero recién actualiza los agregados del miembro al cerrar la ronda.
- **SSE**: `RoomEventBroker` mantiene conexiones en memoria (una sola instancia) y manda un snapshot distinto por jugador (`me`). El snapshot inicial se envía al suscribirse; los eventos son `snapshot`, `reaction`, `closed` y comentarios `ping` de heartbeat. La opción correcta y la explicación solo se incluyen cuando `reveal_until` está activo.
- **Concurrencia**: transiciones con lock pesimista por sala y `@Version`; publicar eventos después del commit. La limpieza (`RoomCleanupService`) borra respuestas, preguntas, miembros y generaciones antes de la sala.
- **Configuración**: `api.rooms.*` en `application.yml` (`max-players: 40`, tiempos, expiraciones, `sse-timeout-ms`, `heartbeat-ms`, `reaction-cooldown-ms`, `scheduler-enabled`).

## Comandos

```bash
# Desarrollo (levanta app + MySQL)
docker-compose up --build -d

# O solo la app (requiere DBs corriendo y .env presente)
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# Tests
./mvnw test                     # ./mvnw verify  → agrega reporte JaCoCo

# Build
./mvnw clean package            # genera target/proyecto2025-be-*.jar

# Swagger
# http://localhost:8080/api/v1/swagger-ui
```

No hay linter/checkstyle configurado. CI: `.github/workflows/code-coverage.yml`.

## Configuración y secretos

- Las variables se cargan desde `.env` (`spring.config.import: optional:file:.env[.properties]`) — plantilla en **`.env.template`**: `MYSQL_*`/`SPRING_DATASOURCE_*`, `OPENAI_API_KEY`, `JWT_SECRET_KEY`, `JWT_EXPIRATION`, `GOOGLE_CLIENT_ID`, `MAIL_*`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_NOTIFICATION_URL` (URL HTTPS pública del webhook), `MP_WEBHOOK_SECRET` (clave del panel para validar `x-signature`), `MP_PLAN_PRICE` (default `10000` ARS), `FE_DOMAIN`
- En desarrollo usar credenciales **TEST** de Mercado Pago y un túnel HTTPS (ngrok/cloudflared) apuntando al backend para `MP_NOTIFICATION_URL`; `auto_return` solo se envía si `FE_DOMAIN` es HTTPS (Mercado Pago rechaza el checkout con back URLs `http://localhost`)
- **El webhook `POST /suscriptions/payment` es público** (Mercado Pago no envía JWT): la autenticidad se valida con la firma `x-signature` y consultando el pago server-side. Si `MP_WEBHOOK_SECRET` está vacío, la validación de firma se omite (solo para desarrollo)
- **Nunca** hardcodear secretos ni commitear `.env`; usar siempre variables de entorno
- La API key de OpenAI se inyecta en `LLMConfig` (`@ConfigurationProperties(prefix = "llm")`)

## Instrucciones para modificar el código

### Dónde agregar cada cosa
- **Nuevo endpoint** → `controller/` (+ anotaciones Swagger) → lógica en `service/` → acceso a datos en `repository/`
- **Nueva entidad MySQL** → `model/` con `@Entity` + repositorio JPA en `repository/`
- **Cambio de esquema** → nueva migración Flyway en `src/main/resources/db/migration` + ajustar la entidad JPA (nunca `ddl-auto: update`)
- **Configuración nueva** → `configuration/` (o `application*.yml` si es por perfil)
- **Errores** → mensajes en `constants/Exceptions.java`, manejo en `exceptions/GlobalExceptionHandler.java`
- **Cambiar el comportamiento del LLM** → system prompt en `constants/LLM.java`, modelo/reasoning effort en `LLMConfig` + `application-{dev,prod}.yml`

### Testing (obligatorio)
- Todo cambio funcional debe llegar con tests: unitarios en `src/test/.../unittests/`, de integración en `integrationtests/` (siguiendo los patrones de los tests existentes)
- Ejecutar `./mvnw verify` antes de dar el cambio por terminado (compila + tests + JaCoCo)
- Los tests corren con H2 y Flyway deshabilitado. Antes de desplegar un cambio de esquema, validar las migraciones y `ddl-auto: validate` contra una copia MySQL de producción (ver `C:\Users\Guglieffo\.opencode\plan\migracion-mysql.md`)
- Los tests de salas usan bases H2 propias (`@TestPropertySource` con `jdbc:h2:mem:roomtest_*`) para no compartir datos con el resto de los contextos. Si agregás una clase de sala, usá una URL propia y limpiá las tablas de sala antes de borrar usuarios.

### Seguridad
- **No modificar** `WebSecurityConfig`, `JwtUtil`, `AuthTokenFilter`, `AuthEntryPointJwt` sin revisión y justificación explícita: controlan qué rutas son públicas y cómo se valida el JWT
- Al agregar un endpoint nuevo, decidir explícitamente si va en la lista de `permitAll()` o queda protegido
- El contexto de usuario (rol/ID) se lee con `security/UserContext` — no confiar en datos de usuario enviados por el body si vienen del token
- Validar inputs (`spring-boot-starter-validation`, `@Valid`/`@Validated`)

### Estilo
- Inglés para identificadores de código, dominio en español (`Pregunta`, `Prompter`, `topico`) — respetar la nomenclatura existente
- Lombok (`@Data`, `@RequiredArgsConstructor`, `@SuperBuilder`) en lugar de boilerplate manual
- Interfaces de servicio para dependencias de controller (ej. `IQuestion`)
