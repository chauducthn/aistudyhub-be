package com.studyhub.aistudyhubbe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService;

@SpringBootTest
@ActiveProfiles("mysql")
class AistudyhubBeApplicationTests {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentTextExtractionService documentTextExtractionService;

    @Test
    void contextLoads() {
    }

    @Test
    void testOcrScannedPdf() throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument();
        org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
        document.addPage(page);
        
        org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
        contentStream.setLineWidth(3);
        contentStream.moveTo(100, 100);
        contentStream.lineTo(200, 200);
        contentStream.stroke();
        contentStream.close();
        
        java.nio.file.Path pdfPath = java.nio.file.Files.createTempFile("scanned-", ".pdf");
        document.save(pdfPath.toFile());
        document.close();
        
        System.out.println("Running extraction on scanned PDF: " + pdfPath.toAbsolutePath());
        DocumentTextExtractionService.ExtractionResult result = 
            documentTextExtractionService.extract(pdfPath, "PDF");
            
        System.out.println("=== OCR RESULT ===");
        System.out.println("Status: " + result.status());
        System.out.println("Text: " + result.text());
        System.out.println("Error: " + result.error());
        System.out.println("==================");
        
        java.nio.file.Files.deleteIfExists(pdfPath);
    }
}
