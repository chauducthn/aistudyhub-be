package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void userCanReportPublicDocumentAndAdminCanResolveIt() throws Exception {
        String ownerToken = registerAndGetToken("report-owner" + System.currentTimeMillis() + "@test.com");
        String reporterToken = registerAndGetToken("reporter" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("report-admin" + System.currentTimeMillis() + "@test.com");

        Integer documentId = uploadTextDocument(ownerToken, "Public Notes", "reported content");
        updateVisibility(ownerToken, documentId, "PUBLIC");

        MvcResult reportResult = mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"MISLEADING_CONTENT\",\"description\":\"Wrong definition\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("MISLEADING_CONTENT"))
                .andReturn();

        Integer reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Duplicate\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(reportId));

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"documentStatus\":\"HIDDEN\",\"adminNote\":\"Confirmed issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.documentStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.data.adminNote").value("Confirmed issue"));

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotReportPrivateOrOwnDocument() throws Exception {
        String ownerToken = registerAndGetToken("private-report-owner" + System.currentTimeMillis() + "@test.com");
        String reporterToken = registerAndGetToken("private-reporter" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Private Notes", "private content");

        mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Cannot access\"}"))
                .andExpect(status().isNotFound());

        updateVisibility(ownerToken, documentId, "PUBLIC");

        mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Own document\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanRejectReportWithoutChangingDocumentStatus() throws Exception {
        String ownerToken = registerAndGetToken("reject-owner" + System.currentTimeMillis() + "@test.com");
        String reporterToken = registerAndGetToken("reject-reporter" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("reject-admin" + System.currentTimeMillis() + "@test.com");

        Integer documentId = uploadTextDocument(ownerToken, "Clean Notes", "clean content");
        updateVisibility(ownerToken, documentId, "PUBLIC");

        MvcResult reportResult = mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Not actually bad\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Integer reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"adminNote\":\"Invalid report\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.documentStatus").value("PUBLIC"));

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk());
    }

    private Integer uploadTextDocument(String token, String title, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report-notes.txt",
                "text/plain",
                content.getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void updateVisibility(String token, Integer documentId, String visibilityStatus) throws Exception {
        mockMvc.perform(patch("/api/documents/" + documentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + visibilityStatus + "\"}"))
                .andExpect(status().isOk());
    }

    private String registerAndGetToken(String email) throws Exception {
        String registerJson = """
                {"email":"%s","password":"secret123","fullName":"Report User"}
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
        admin.setFullName("Report Admin");
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
