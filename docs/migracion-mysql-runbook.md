# Runbook — corte a MySQL único

> **Documento histórico del corte del 2026-10-27.** El runtime ya no usa Mongo;
> se conserva como registro operativo. Las migraciones V1–V3 siguen vigentes.
>
> Rama: `migration/mysql` · Migraciones: V1/V2/V3 · Migrador: perfil `migrate`
> Última actualización: 2026-10-27

> **Producción real:** el compose vive en `/home/juan/triviando/docker-compose.yml`,
> proyecto `triviando`, servicio **`backend`** (imagen `triviando-backend`).
> Donde este runbook diga `app`, en ese servidor usar `backend`.
> En producción **no** se usa `docker-compose.prod.yml`: ese override es para el
> compose del repo (`app`); el compose de `/home/juan/triviando` ya define `prod`.

## 0. Antes de empezar

- [x] Frontend adaptado a IDs numéricos (`questionId`, `jobId`): rama `feature/mysql-numeric-ids`, commit `211097c`; `tsc` y `next build` OK, Jest 389 tests.
- [ ] El PR está revisado y la imagen se construyó desde el commit final.
- [ ] Hay una ventana de 1–2 h y la app puede quedar en mantenimiento.
- [ ] Se hizo un ensayo completo con un dump fresco de producción (ver §6).
- [ ] Se conoce el usuario de prueba y se tiene un JWT válido para los smoke tests.

Comandos base en el VPS (directorio del compose):

```bash
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
STAMP=$(date +%Y%m%d-%H%M)
```

---

## 1. Congelar escrituras y respaldar

```bash
# 1. Bajar la app (el frontend queda en mantenimiento)
docker compose stop app

# 2. Forzar jobs activos a FAILED si quedaron colgados (revisar antes)
#    En esta versión los jobs viven en MySQL:
docker exec mysql-db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" flashcards -e "
  UPDATE generation_jobs SET status=\"FAILED\", error=\"Corte de migración\", updated_at=NOW(6)
  WHERE status IN (\"PENDING\",\"PROCESSING\");"' 2>/dev/null || true

# 3. Backup MySQL (sin PROCESS: --no-tablespaces es obligatorio)
docker exec mysql-db sh -c \
  'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
   --no-tablespaces --single-transaction --routines --triggers --set-gtid-purged=OFF \
   "$MYSQL_DATABASE"' > backup-mysql-$STAMP.sql

# 4. Backup Mongo (queda intacto como rollback)
docker exec mongo-db sh -c 'mongodump --archive --gzip' > backup-mongo-$STAMP.archive.gz

# 5. Checksums y copia fuera del VPS
sha256sum backup-mysql-$STAMP.sql backup-mongo-$STAMP.archive.gz
scp backup-mysql-$STAMP.sql backup-mongo-$STAMP.archive.gz usuario@destino:/ruta/
```

**No continuar** si el restore del backup no está probado. Para el restore de MySQL, usar un contenedor scratch como en el ensayo.

---

## 2. Migrador: dry-run

Construir la imagen final:

```bash
docker compose build app
```

Dry-run (no escribe nada; el perfil `migrate` usa `flyway.target=2` y `ddl-auto=none`):

```bash
docker compose run --rm --no-deps \
  -e SPRING_PROFILES_ACTIVE=prod,migrate \
  app --app.migration.dry-run=true
```

Revisar el reporte:

```text
Legacy: questions=<N> unique=<U> duplicateEnunciados=<D> feedback=<F> jobs=<J>
Topics: distinct=<T> merged=<M>
Questions: expectedOptions=<N*4>
Answers: toUpdate=<A> unmapped=0
Orphan users=0 invalid questions=0
```

**Abortar** si `unmapped > 0`, `Orphan users > 0` o `invalid questions > 0`. Guardar el log como evidencia.

---

## 3. Migrador: ejecución real

```bash
docker compose run --rm --no-deps \
  -e SPRING_PROFILES_ACTIVE=prod,migrate \
  app --app.migration.dry-run=false
```

Resultado esperado (referencia del ensayo 2026-10-27: 9 usuarios, 185 respuestas, 382 preguntas):

```text
Topics: inserted=18 existing=0
Questions: inserted=382 existing=0
Options: inserted=1528 skipped=0
Answers: updated=185 unmapped=0
```

Si falla: no seguir a V3. La app vieja puede volver a levantarse porque V2 solo agregó tablas.
El migrador es idempotente: se puede reintentar sin duplicar.

---

## 4. Verificación pre-V3

```bash
docker exec mysql-db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N flashcards -e "
  SELECT \"topics\", COUNT(*) FROM topics
  UNION ALL SELECT \"questions\", COUNT(*) FROM questions
  UNION ALL SELECT \"options\", COUNT(*) FROM question_options
  UNION ALL SELECT \"legacy_map\", COUNT(*) FROM legacy_id_map
  UNION ALL SELECT \"answers_mapped\", COUNT(*) FROM answer WHERE question_id_new IS NOT NULL
  UNION ALL SELECT \"answers_unmapped\", COUNT(*) FROM answer WHERE question_id_new IS NULL;"' 2>/dev/null
```

Esperado: `topics=18`, `questions=382`, `options=1528`, `legacy_map=382`,
`answers_unmapped=0`.

Verificar que no haya preguntas sin 4 opciones ni sin exactamente una correcta:

```bash
docker exec mysql-db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N flashcards -e "
  SELECT COUNT(*) FROM (SELECT question_id FROM question_options GROUP BY question_id HAVING COUNT(*)<>4) x;
  SELECT COUNT(*) FROM (SELECT question_id FROM question_options GROUP BY question_id HAVING SUM(is_correct)<>1) x;"' 2>/dev/null
```

---

## 5. Aplicar V3 y levantar la app

V3 la aplica Flyway al arrancar la app nueva. El `ALTER ... NOT NULL` falla si
quedó alguna respuesta sin mapear: es una guarda deliberada.

```bash
$COMPOSE up -d app
$COMPOSE logs -f app
```

Esperar:

```text
Migrating schema `flashcards` to version "3 - finalize content"
Successfully applied 1 migration
Started Proyecto2025BeApplication
```

Si falla la validación de Hibernate, **no abrir tráfico**: restaurar el backup y
volver a la imagen anterior.

---

## 6. Smoke tests (antes de abrir tráfico)

Con un JWT de un usuario de prueba:

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN="<jwt>"

curl -fsS -H "Authorization: Bearer $TOKEN" "$API/preguntas/1"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/preguntas?userId=4"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/preguntas?userId=4&topico=Yamaha%20R3"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/preguntas/topicos/4"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/users/score/4"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/users/ranking?page=0&size=5"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/users/racha/4"
```

Respuestas y feedback (usar un usuario sin respuestas para esa pregunta):

```bash
curl -fsS -X POST "$API/answers" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"questionId":1,"user":{"id":8},"optionSelected":"A","millisecondsSpent":1000}'

# Repetir el mismo POST debe devolver 400 "Esta pregunta ya ha sido respondida"

curl -fsS -X POST "$API/preguntas/send-feedback" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":8,"questionId":1,"feedbackType":"POSITIVE"}'
```

Jobs (JSON):

```bash
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/preguntas/document/jobs/1"
```

Luego:

1. Deployar el frontend con IDs numéricos.
2. Smoke test del flujo completo desde la UI.
3. Recién ahí quitar el modo mantenimiento y abrir tráfico.
4. Monitorear 1 h: logs, errores 5xx, latencia y jobs.

---

## 7. Rollback

### Si falla antes de V3

- `docker compose stop app`
- Volver a la imagen anterior (`docker compose up -d app` con el tag previo).
- Mongo sigue intacto; V2 solo agregó tablas y la app vieja las ignora.
- No hace falta restaurar MySQL.

### Si falla después de V3

V3 eliminó `answer.question_id` viejo y la tabla `options`: **retaggear la imagen
vieja no alcanza**, hay que restaurar MySQL. Mongo queda intacto.

```bash
cd /home/juan/triviando
sudo docker compose stop backend

# Restaurar el dump del corte (root del contenedor; el dump trae los CREATE TABLE)
sudo docker exec mysql-db sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
   "DROP DATABASE flashcards; CREATE DATABASE flashcards CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'
sudo docker exec -i mysql-db sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" flashcards' \
  < /home/triviando-backup/dumps/cutover-mysql-20261027-1728.sql

# Volver a la imagen anterior
sudo docker tag triviando-backend:rollback-20261027-1728 triviando-backend:latest
sudo docker compose up -d --force-recreate backend
```

El backup de Mongo no se restaura: la app vieja sigue leyendo la colección intacta.
**No borrar el volumen de Mongo ni los backups hasta 7 días después del corte.**

---

## 8. Post-corte

- Mantener Mongo detenido con el volumen intacto 7 días.
- No borrar `legacy_id_map` hasta validar que no hace falta auditar ObjectIds.
- A las 48 h revisar: usuarios nuevos, respuestas, ranking, feedback, jobs y errores.
- Después de 7 días:
  - Quitar `spring-boot-starter-data-mongodb` del `pom.xml`.
  - Quitar `mongo-db` de `docker-compose.yml` y `MONGO_*` de `.env`.
  - Eliminar `migration/` y, si ya no se audita, `legacy_id_map`.
- V4 opcional: normalizar `users.account` (ordinal → string) y `users.privileges`.

---

## 9. Evidencia del ensayo (2026-10-27)

- Dump fresco: 9 usuarios, 185 respuestas, 382 preguntas.
- Dry-run: 382 preguntas, 341 enunciados únicos, 41 duplicados preservados, 18 tópicos, 0 huérfanos.
- Real: 18 tópicos, 382 preguntas, 1528 opciones, 382 mapeos, 185/185 respuestas.
- V3 por Flyway + `ddl-auto: validate` → `Started`.
- Smoke HTTP: preguntas, tópicos, score, ranking, racha, responder, duplicado (400), feedback (201), job JSON (200), job inexistente (404).
- `./mvnw verify`: 187 tests, 0 fallos.
