package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.service.document.DocxDocumentTextExtractor;
import com.example.proyecto2025_BE.service.document.DocumentTextExtractionService;
import com.example.proyecto2025_BE.service.document.DocumentTextNormalizer;
import com.example.proyecto2025_BE.service.document.PdfDocumentTextExtractor;
import com.example.proyecto2025_BE.service.document.PlainTextDocumentTextExtractor;
import com.example.proyecto2025_BE.service.document.UploadedFileValidator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Document Text Extraction Service Tests")
class DocumentTextExtractionServiceTest {

    private static final String LONG_TEXT =
            "La célula es la unidad estructural y funcional de todos los seres vivos. "
                    + "Contiene organelas como el núcleo, las mitocondrias y el retículo endoplasmático. "
                    + "Su descubrimiento se atribuye a Robert Hooke en el siglo XVII.";

    private DocumentProperties properties;
    private DocumentTextExtractionService service;

    @BeforeEach
    void setUp() {
        properties = new DocumentProperties();
        service = new DocumentTextExtractionService(
                new UploadedFileValidator(properties),
                new DocumentTextNormalizer(properties),
                List.of(
                        new PdfDocumentTextExtractor(properties),
                        new DocxDocumentTextExtractor(),
                        new PlainTextDocumentTextExtractor()));
    }

    @Test
    @DisplayName("Extrae texto de un TXT y lo normaliza")
    void extraeTxt() {
        MockMultipartFile file = new MockMultipartFile("file", "apunte.txt", "text/plain",
                LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        String result = service.extract(file);

        assertTrue(result.contains("La célula es la unidad estructural"));
        assertEquals(LONG_TEXT, result);
    }

    @Test
    @DisplayName("Extrae texto de un MD")
    void extraeMd() {
        MockMultipartFile file = new MockMultipartFile("file", "apunte.md", "text/markdown",
                ("# Título\n\n" + LONG_TEXT).getBytes(StandardCharsets.UTF_8));

        String result = service.extract(file);

        assertTrue(result.contains("Título"));
        assertTrue(result.contains("Robert Hooke"));
    }

    @Test
    @DisplayName("Extrae texto de un DOCX")
    void extraeDocx() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "apunte.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                crearDocx(LONG_TEXT));

        String result = service.extract(file);

        assertTrue(result.contains("unidad estructural y funcional"));
        assertTrue(result.contains("Robert Hooke"));
    }

    @Test
    @DisplayName("Extrae texto de un PDF")
    void extraePdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "apunte.pdf", "application/pdf",
                crearPdf(LONG_TEXT));

        String result = service.extract(file);

        assertTrue(result.contains("unidad estructural y funcional"));
        assertTrue(result.contains("Robert Hooke"));
    }

    @Test
    @DisplayName("Rechaza un archivo vacío")
    void rechazaArchivoVacio() {
        MockMultipartFile file = new MockMultipartFile("file", "vacio.txt", "text/plain", new byte[0]);

        ValidationException exception = assertThrows(ValidationException.class, () -> service.extract(file));

        assertEquals("El archivo está vacío", exception.getMessage());
    }

    @Test
    @DisplayName("Rechaza un archivo que supera el tamaño máximo")
    void rechazaArchivoGrande() {
        properties.setMaxFileSizeBytes(10);
        MockMultipartFile file = new MockMultipartFile("file", "grande.txt", "text/plain",
                LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        ValidationException exception = assertThrows(ValidationException.class, () -> service.extract(file));

        assertTrue(exception.getMessage().contains("tamaño máximo"));
    }

    @Test
    @DisplayName("Rechaza extensiones no soportadas")
    void rechazaExtensionNoSoportada() {
        MockMultipartFile file = new MockMultipartFile("file", "planilla.xlsx", "application/vnd.ms-excel",
                LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        ValidationException exception = assertThrows(ValidationException.class, () -> service.extract(file));

        assertTrue(exception.getMessage().contains("Formato de archivo no soportado"));
    }

    @Test
    @DisplayName("Rechaza un PDF cuya firma no coincide")
    void rechazaFirmaPdfInvalida() {
        MockMultipartFile file = new MockMultipartFile("file", "falso.pdf", "application/pdf",
                LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        ValidationException exception = assertThrows(ValidationException.class, () -> service.extract(file));

        assertTrue(exception.getMessage().contains("Formato de archivo no soportado"));
    }

    @Test
    @DisplayName("Rechaza un PDF sin capa de texto")
    void rechazaPdfSinTexto() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "escaneado.pdf", "application/pdf", crearPdf(""));

        ValidationException exception = assertThrows(ValidationException.class, () -> service.extract(file));

        assertTrue(exception.getMessage().contains("No se pudo extraer texto"));
    }

    @Test
    @DisplayName("Acota el texto extraído al máximo configurado")
    void acotaTextoExtraido() {
        properties.setMaxExtractedChars(60);
        MockMultipartFile file = new MockMultipartFile("file", "apunte.txt", "text/plain",
                LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        String result = service.extract(file);

        assertEquals(60, result.length());
    }

    private byte[] crearDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] crearPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                if (!text.isEmpty()) {
                    stream.showText(text);
                }
                stream.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }
}
