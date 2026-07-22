package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.QwenChatClient;
import com.studyhub.aistudyhubbe.service.QwenChatClient.QwenResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatbotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private QwenChatClient qwenChatClient;

    @Test
    void userCanChatWithStudyAssistantAndMetricsTrackTotalCalls() throws Exception {
        when(qwenChatClient.isConfigured()).thenReturn(true);
        when(qwenChatClient.generate(any(), any(), any(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            String context = invocation.getArgument(2);
            String response = prompt + System.lineSeparator() + context;
            return new QwenResult(response, "TEST_STUDY_ASSISTANT");
        });

        String ownerToken = registerAndGetToken("chat-owner" + System.currentTimeMillis() + "@test.com");
        String viewerToken = registerAndGetToken("chat-viewer" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("chat-admin" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Chat Notes", "chat content");

        mockMvc.perform(post("/api/chatbot/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Summarize polymorphism\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.model").value("TEST_STUDY_ASSISTANT"))
                .andExpect(jsonPath("$.data.response").value(org.hamcrest.Matchers.containsString("polymorphism")));

        mockMvc.perform(post("/api/chatbot/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":%d,"message":"Create quiz questions"}
                                """.formatted(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.documentTitle").value("Chat Notes"))
                .andExpect(jsonPath("$.data.response").value(org.hamcrest.Matchers.containsString("chat content")));

        mockMvc.perform(post("/api/chatbot/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":%d,"message":"Read private document"}
                                """.formatted(documentId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("permission")));

        mockMvc.perform(patch("/api/documents/%d/visibility".formatted(documentId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/chatbot/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":%d,"message":"Summarize public notes"}
                                """.formatted(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.response").value(org.hamcrest.Matchers.containsString("chat content")));

        mockMvc.perform(get("/api/chatbot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/chatbot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(delete("/api/chatbot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chatbot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/admin/dashboard/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatbotApiCalls").value(3));
    }

    private Integer uploadTextDocument(String token, String title, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chat-notes.txt",
                "text/plain",
                content.getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        Integer documentId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        documentRepository.findById(documentId.longValue()).ifPresent(document -> {
            document.setExtractedText(content);
            document.setExtractionStatus(DocumentExtractionStatus.EXTRACTED);
            documentRepository.saveAndFlush(document);
        });
        return documentId;
    }

    private String registerAndGetToken(String email) throws Exception {
        String registerJson = """
                {"email":"%s","password":"secret123","fullName":"Chat User"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String createAdminAndGetToken(String email) throws Exception {
        User admin = new User();
        admin.setEmail(email);
        admin.setFullName("Chat Admin");
        admin.setPasswordHash(passwordEncoder.encode("Admin12345"));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Admin12345"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
