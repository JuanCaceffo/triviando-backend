package com.example.proyecto2025_BE.constants;

public class Exceptions {
	public static final String NOT_FOUND = "Recurso no encontrado";
	public static final String VALIDATION_ERROR = "El valor no puede ser vacio";
	public static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos";
	public static final String FILE_EMPTY = "El archivo está vacío";
	public static final String FILE_TOO_LARGE = "El archivo supera el tamaño máximo permitido de 10 MB";
	public static final String INVALID_FILE_TYPE = "Formato de archivo no soportado. Usá PDF, DOCX, TXT o MD";
	public static final String INVALID_FILE = "No se pudo leer el archivo. Verificá que no esté dañado ni protegido con contraseña";
	public static final String EMPTY_DOCUMENT_TEXT = "No se pudo extraer texto suficiente del archivo. Si es un PDF escaneado, no es posible procesarlo";
	public static final String GENERATION_TOPIC_REQUIRED = "El nombre del tópico es obligatorio";
	public static final String GENERATION_JOB_NOT_FOUND = "No se encontró el proceso de generación";
	public static final String GENERATION_JOB_IN_PROGRESS = "Ya tenés una generación en curso. Esperá a que termine para iniciar otra";
	public static final String GENERATION_JOB_EXPIRED = "El procesamiento del archivo expiró. Intentá nuevamente";
	public static final String GENERATION_FAILED = "No se pudieron generar las preguntas a partir del archivo";
}
