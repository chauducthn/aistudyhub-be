package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.extractor.QuickButCruddyTextExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class DocumentTextExtractionService {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 200_000;

    private final ChatbotAiResponder chatbotAiResponder;

    public DocumentTextExtractionService(ChatbotAiResponder chatbotAiResponder) {
        this.chatbotAiResponder = chatbotAiResponder;
    }

    public ExtractionResult extract(Path documentPath, String fileType) {
        try {
            String text = switch (normalizeFileType(fileType)) {
                case "PDF" -> extractPdf(documentPath);
                case "DOC" -> extractDoc(documentPath);
                case "DOCX" -> extractDocx(documentPath);
                case "PPT" -> extractPpt(documentPath);
                case "PPTX" -> extractPptx(documentPath);
                case "XLS", "XLSX" -> extractSpreadsheet(documentPath);
                case "TXT", "MD", "CSV" -> Files.readString(documentPath, StandardCharsets.UTF_8);
                default -> throw new IOException("Text extraction is not supported for this file type");
            };

            String normalizedText = normalizeExtractedText(text);
            if (normalizedText.isBlank()) {
                return ExtractionResult.failed("No readable text was found in the document");
            }

            return ExtractionResult.extracted(normalizedText);
        } catch (Exception ex) {
            return ExtractionResult.failed("Could not extract text: " + safeErrorMessage(ex));
        }
    }

    private String extractPdf(Path documentPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(documentPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            if (text != null && text.trim().length() >= 10) {
                return text;
            }

            org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            StringBuilder ocrText = new StringBuilder();
            int pageCount = Math.min(document.getNumberOfPages(), 10);
            for (int page = 0; page < pageCount; page++) {
                try {
                    java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 200);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(bim, "png", baos);
                    byte[] imageBytes = baos.toByteArray();
                    bim.flush();

                    String pageText = chatbotAiResponder.performOcr(imageBytes);
                    if (pageText != null && !pageText.isBlank()) {
                        ocrText.append(pageText).append("\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return ocrText.toString();
        }
    }

    private String extractDoc(Path documentPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(documentPath);
                WordExtractor extractor = new WordExtractor(inputStream)) {
            return extractor.getText();
        }
    }

    private String extractDocx(Path documentPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(documentPath);
                XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractPpt(Path documentPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(documentPath)) {
            QuickButCruddyTextExtractor extractor = new QuickButCruddyTextExtractor(inputStream);
            try {
                return extractor.getTextAsString();
            } finally {
                extractor.close();
            }
        }
    }

    private String extractPptx(Path documentPath) throws IOException {
        StringBuilder text = new StringBuilder();

        try (InputStream inputStream = Files.newInputStream(documentPath);
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("ppt/slides/slide") && entryName.endsWith(".xml")) {
                    String slideXml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    appendLine(text, stripXmlText(slideXml));
                }
            }
        }

        return text.toString();
    }

    private String extractSpreadsheet(Path documentPath) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(documentPath.toFile(), null, true)) {
            for (Sheet sheet : workbook) {
                appendLine(text, "Sheet: " + sheet.getSheetName());
                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (!value.isBlank()) {
                            if (!rowText.isEmpty()) {
                                rowText.append(" | ");
                            }
                            rowText.append(value);
                        }
                    }
                    appendLine(text, rowText.toString());
                }
            }
        }

        return text.toString();
    }

    private void appendLine(StringBuilder text, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(System.lineSeparator());
        }
        text.append(line.trim());
    }

    private String normalizeFileType(String fileType) {
        return fileType == null ? "" : fileType.trim().toUpperCase();
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        if (normalized.length() > MAX_EXTRACTED_TEXT_LENGTH) {
            return normalized.substring(0, MAX_EXTRACTED_TEXT_LENGTH);
        }
        return normalized;
    }

    private String stripXmlText(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }

        return xml
                .replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 220 ? message.substring(0, 220) : message;
    }

    public record ExtractionResult(
            DocumentExtractionStatus status,
            String text,
            String error,
            Instant extractedAt
    ) {
        static ExtractionResult extracted(String text) {
            return new ExtractionResult(DocumentExtractionStatus.EXTRACTED, text, null, Instant.now());
        }

        static ExtractionResult failed(String error) {
            return new ExtractionResult(DocumentExtractionStatus.FAILED, null, error, Instant.now());
        }
    }
}
