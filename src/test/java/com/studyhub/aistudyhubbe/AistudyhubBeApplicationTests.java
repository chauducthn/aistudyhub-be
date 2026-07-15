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
}
