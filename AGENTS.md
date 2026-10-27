# AGENTS.md

Guía para agentes de IA que trabajan en este repositorio.

## Visión general

**Triviando** es una API REST que crea y regenera quizzes (preguntas) mediante IA. También gestiona usuarios, autenticación (JWT + Google), suscripciones FREE/PREMIUM (MercadoPago), rankings, estadísticas y rachas.

Proyecto académico (UNSAM, grupo "Nullpointer"). El frontend vive en otro repo (Vercel). Detalles de proceso (JIRA, Git Flow completo) están en `README.md`.

## Stack y arquitectura

- **Java 21 + Spring Boot 3.4.3** (Maven, wrapper `mvnw`), puerto `8080`, context path **`/api/v1`**
- **IA**: LangChain4j (`AiServices`) con OpenAI `gpt-4o-mini` (temperature 0, JSON schema estricto, chat memory de 10 mensajes)
- **Auth**: JWT (jjwt) + BCrypt + Google Sign-In; **Pagos**: MercadoPago; **Docs**: Swagger (`/api/v1/swagger-ui`)
- **Perfiles**: `dev` (default), `prod`, `test` (`src/main/resources/application*.yml`)
- **Despliegue**: `Dockerfile` + `docker-compose.yml` (app + mysql + mongo)

### Paquetes base: `src/main/java/com/example/proyecto2025_BE/`

| Paquete | Contenido |
|---|---|
| `controller` | Endpoints REST (Auth, User, Question, Answer, Subscription) |
| `service` | Lógica de negocio (`IQuestion`, `LLMApiClientService`, `UserService`, etc.) |
| `repository` | Repositorios Spring Data (JPA y Mongo) |
| `model` | Entidades, DTOs (`model/dto`), prompters (`model/prompter`), MercadoPago (`model/mp`), rachas (`model/racha`) |
| `configuration` | Beans: `LLMConfig`, `MPConfig`, `MySQLConfig`, Swagger, CORS, serializadores |
| `security` | `WebSecurityConfig`, `JwtUtil`, `AuthTokenFilter`, `UserContext` |
| `views` | DTOs de request/response de la API + esquemas Swagger |
| `constants` | Prompts del LLM (`LLM.java`), mensajes de error, patrones |
| `exceptions` | `GlobalExceptionHandler` |

## Arquitectura de BD dual

- **MySQL 8 + JPA/Hibernate** (`ddl-auto: update` — **no hay Flyway ni Liquibase**; el esquema evoluciona por entidades):
  - `User` (`users`), `Answer`, `Option` (`options`), `PasswordResetToken` (`password_reset_tokens`)
- **MongoDB** (`@Document`):
  - `Pregunta` (`preguntas`), `Respuesta` (`respuestas_usuario`), `Feedback`
  - `GenerationJob` (`generation_jobs`) — jobs asíncronos de generación desde archivo (índice TTL 24 h)
- **Tests**: H2 en memoria (`application-test.yml`)

Consecuencia: cambios de esquema MySQL = modificar entidades `@Entity`; cambios en preguntas/feedback = modificar entidades `@Document`. No esperar migraciones.

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
5. `ModelCommunication` (interfaz construida en `configuration/LLMConfig` vía LangChain4j `AiServices`) devuelve un DTO `QuestionList` (`model/dto/llm`) → se mapea a `Pregunta` → `QuestionService` guarda en Mongo
6. El rol (FREE/PREMIUM, enum `Account`) y el userId llegan del JWT vía `security/UserContext`. Los usuarios FREE no tienen límite diario de temas ni de preguntas; la generación desde archivos es exclusiva de PREMIUM (403 para el resto).

### Generación desde archivo (asíncrona)

1. `POST /preguntas/document/generate` pasa por `GenerationRequestPolicy` (solo plan PREMIUM, tópico no duplicado, un job activo por usuario) y extrae el texto en memoria con `DocumentTextExtractionService` y las estrategias de `service/document` (PDFBox/POI/plano; validación, normalización y límites en `api.documents`).
2. `GenerationJobService` guarda un `GenerationJob` en Mongo (`PENDING`) y `DocumentGenerationWorker` lo encola (`@Async("documentGenerationExecutor")`, pool acotado en `AsyncConfig`).
3. Si el texto supera `api.documents.prompt-budget-chars`, `DocumentContextBuilder` hace map-reduce con `DocumentSummarizer` (LangChain4j, system prompt propio) muestreando bloques distribuidos por todo el documento.
4. Con el contexto resumido construye un `FilePrompter` mediante `FilePrompter.forDocument(...)` (extiende `TopicPrompter` y exige contexto) y reutiliza `LLMApiClientService.generate` (dedup, emoji, persistencia en `preguntas`).
5. El frontend consulta el job por polling (~2,5 s, timeout 5 min). `GenerationJobMapper` traduce entidad ↔ DTO; el texto extraído se borra del job al terminar y el documento Mongo expira por TTL.
6. PDFs escaneados no tienen OCR: se devuelve error de extracción.

## Comandos

```bash
# Desarrollo (levanta app + MySQL + MongoDB)
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

- Las variables se cargan desde `.env` (`spring.config.import: optional:file:.env[.properties]`) — plantilla en **`.env.template`**: `MONGO_*`, `MYSQL_*`/`SPRING_DATASOURCE_*`, `OPENAI_API_KEY`, `JWT_SECRET_KEY`, `JWT_EXPIRATION`, `GOOGLE_CLIENT_ID`, `MAIL_*`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_NOTIFICATION_URL` (URL HTTPS pública del webhook), `MP_WEBHOOK_SECRET` (clave del panel para validar `x-signature`), `MP_PLAN_PRICE` (default `10000` ARS), `FE_DOMAIN`
- En desarrollo usar credenciales **TEST** de Mercado Pago y un túnel HTTPS (ngrok/cloudflared) apuntando al backend para `MP_NOTIFICATION_URL`; `auto_return` solo se envía si `FE_DOMAIN` es HTTPS (Mercado Pago rechaza el checkout con back URLs `http://localhost`)
- **El webhook `POST /suscriptions/payment` es público** (Mercado Pago no envía JWT): la autenticidad se valida con la firma `x-signature` y consultando el pago server-side. Si `MP_WEBHOOK_SECRET` está vacío, la validación de firma se omite (solo para desarrollo)
- **Nunca** hardcodear secretos ni commitear `.env`; usar siempre variables de entorno
- La API key de OpenAI se inyecta en `LLMConfig` (`@ConfigurationProperties(prefix = "llm")`)

## Instrucciones para modificar el código

### Dónde agregar cada cosa
- **Nuevo endpoint** → `controller/` (+ anotaciones Swagger) → lógica en `service/` → acceso a datos en `repository/`
- **Nueva entidad MySQL** → `model/` con `@Entity` + repositorio JPA en `repository/`
- **Nueva entidad Mongo** → `model/` con `@Document` + repositorio `MongoRepository`
- **Configuración nueva** → `configuration/` (o `application*.yml` si es por perfil)
- **Errores** → mensajes en `constants/Exceptions.java`, manejo en `exceptions/GlobalExceptionHandler.java`
- **Cambiar el comportamiento del LLM** → system prompt en `constants/LLM.java`, modelo/temperatura en `LLMConfig` + `application-{dev,prod}.yml`

### Testing (obligatorio)
- Todo cambio funcional debe llegar con tests: unitarios en `src/test/.../unittests/`, de integración en `integrationtests/` (siguiendo los patrones de los tests existentes)
- Ejecutar `./mvnw verify` antes de dar el cambio por terminado (compila + tests + JaCoCo)

### Seguridad
- **No modificar** `WebSecurityConfig`, `JwtUtil`, `AuthTokenFilter`, `AuthEntryPointJwt` sin revisión y justificación explícita: controlan qué rutas son públicas y cómo se valida el JWT
- Al agregar un endpoint nuevo, decidir explícitamente si va en la lista de `permitAll()` o queda protegido
- El contexto de usuario (rol/ID) se lee con `security/UserContext` — no confiar en datos de usuario enviados por el body si vienen del token
- Validar inputs (`spring-boot-starter-validation`, `@Valid`/`@Validated`)

### Estilo
- Inglés para identificadores de código, dominio en español (`Pregunta`, `Prompter`, `topico`) — respetar la nomenclatura existente
- Lombok (`@Data`, `@RequiredArgsConstructor`, `@SuperBuilder`) en lugar de boilerplate manual
- Interfaces de servicio para dependencias de controller (ej. `IQuestion`)
