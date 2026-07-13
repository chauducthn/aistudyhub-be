package com.studyhub.aistudyhubbe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentTextExtractionServiceTest {

    private DocumentTextExtractionService service;

    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ChatbotAiResponder chatbotAiResponder = org.mockito.Mockito.mock(ChatbotAiResponder.class);
        service = new DocumentTextExtractionService(chatbotAiResponder);
        tempDir = Path.of("target", "text-extraction-test", UUID.randomUUID().toString());
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var paths = Files.walk(tempDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void extractPdf_ReturnsReadableText() throws Exception {
        Path pdfPath = tempDir.resolve("task-two.pdf");
        writePdf(pdfPath, "Task two PDF extraction works");

        DocumentTextExtractionService.ExtractionResult result = service.extract(pdfPath, "PDF");

        assertEquals(DocumentExtractionStatus.EXTRACTED, result.status());
        assertTrue(result.text().contains("Task two PDF extraction works"));
    }

    @Test
    void extractDocx_ReturnsReadableText() throws Exception {
        Path docxPath = tempDir.resolve("task-two.docx");
        writeDocx(docxPath, "Task two DOCX extraction works");

        DocumentTextExtractionService.ExtractionResult result = service.extract(docxPath, "DOCX");

        assertEquals(DocumentExtractionStatus.EXTRACTED, result.status());
        assertTrue(result.text().contains("Task two DOCX extraction works"));
    }

    private void writePdf(Path path, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }

            document.save(path.toFile());
        }
    }

    private void writeDocx(Path path, String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                OutputStream outputStream = Files.newOutputStream(path)) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(outputStream);
        }
    }
}
