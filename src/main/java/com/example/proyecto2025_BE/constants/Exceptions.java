package com.example.proyecto2025_BE.constants;

public class Exceptions {
	public static final String NOT_FOUND = "Recurso no encontrado";
	public static final String VALIDATION_ERROR = "El valor no puede ser vacio";
	public static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos";
	public static final String FILE_EMPTY = "El archivo está vacío";
	public static final String FILE_TOO_LARGE = "El archivo supera el tamaño máximo permitido de %d MB";
	public static final String INVALID_FILE_TYPE = "Formato de archivo no soportado. Usá PDF, DOCX, TXT o MD";
	public static final String INVALID_FILE = "No se pudo leer el archivo. Verificá que no esté dañado ni protegido con contraseña";
	public static final String EMPTY_DOCUMENT_TEXT = "No se pudo extraer texto suficiente del archivo. Si es un PDF escaneado, no es posible procesarlo";
	public static final String GENERATION_TOPIC_REQUIRED = "El nombre del tópico es obligatorio";
	public static final String TOPIC_ALREADY_EXISTS = "Ya existe el tópico que se intenta crear";
	public static final String PREMIUM_ONLY_DOCUMENT_GENERATION = "La creación de temas desde archivos es exclusiva del plan Premium. Actualizá tu plan para usarla.";
	public static final String GENERATION_JOB_NOT_FOUND = "No se encontró el proceso de generación";
	public static final String GENERATION_JOB_IN_PROGRESS = "Ya tenés una generación en curso. Esperá a que termine para iniciar otra";
	public static final String GENERATION_JOB_EXPIRED = "El procesamiento del archivo expiró. Intentá nuevamente";
	public static final String GENERATION_FAILED = "No se pudieron generar las preguntas a partir del archivo";

	public static final String PREMIUM_ONLY_ROOM_CREATION = "La creación de salas en vivo es exclusiva del plan Premium. Actualizá tu plan para usarla.";
	public static final String ROOM_TOPIC_REQUIRED = "El nombre del tema de la sala es obligatorio";
	public static final String ROOM_TOPIC_TOO_LONG = "El nombre del tema no puede superar los 120 caracteres";
	public static final String ROOM_ALREADY_ACTIVE = "Ya tenés una sala activa. Cerrala o esperá a que termine para crear otra";
	public static final String ROOM_CODE_GENERATION_FAILED = "No se pudo crear la sala. Intentá nuevamente";
	public static final String ROOM_NOT_FOUND = "No se encontró la sala";
	public static final String ROOM_ALREADY_STARTED = "La partida ya comenzó; no podés unirte";
	public static final String ROOM_NOT_JOINABLE = "La sala no está disponible para unirse";
	public static final String ROOM_FULL = "La sala está completa";
	public static final String ROOM_ALREADY_IN_ANOTHER = "Ya estás participando en otra sala activa";
	public static final String ROOM_LEFT = "Saliste de esta sala y no podés volver a ingresar";
	public static final String ROOM_ONLY_HOST = "Solo el anfitrión puede realizar esta acción";
	public static final String ROOM_NOT_IN_LOBBY = "La sala todavía no está lista para iniciar";
	public static final String ROOM_MIN_PLAYERS = "Se necesitan al menos 2 jugadores para iniciar";
	public static final String ROOM_NOT_READY = "La sala todavía no tiene preguntas generadas";
	public static final String ROOM_NOT_MEMBER = "No participás de esta sala";
	public static final String ROOM_QUESTION_NOT_FOUND = "No se encontró la pregunta de la sala";
	public static final String ROOM_QUESTION_NOT_CURRENT = "La pregunta ya no está activa";
	public static final String ROOM_ANSWER_WINDOW_CLOSED = "El tiempo para responder terminó";
	public static final String ROOM_ANSWER_ALREADY_SENT = "Ya respondiste esta pregunta";
	public static final String ROOM_OPTION_INVALID = "La opción seleccionada no es válida";
	public static final String ROOM_GENERATION_EMPTY = "La IA no devolvió preguntas válidas para la sala";
	public static final String ROOM_GENERATION_FAILED = "No se pudieron generar las preguntas de la sala";
	public static final String ROOM_REACTION_INVALID = "La reacción no es válida";
	public static final String ROOM_REACTION_TOO_FAST = "Esperá un momento antes de enviar otra reacción";
	public static final String ROOM_REACTION_NOT_ALLOWED = "No se pueden enviar reacciones en este estado";
}
