package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminSummaryRequiresAdminRole() throws Exception {
        String userToken = registerAndGetToken("regular" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("admin" + System.currentTimeMillis() + "@test.com");

        mockMvc.perform(get("/api/admin/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ADMIN_OK"));
    }

    @Test
    void adminCanListUsersAndLockOrUnlockAccount() throws Exception {
        String email = "managed" + System.currentTimeMillis() + "@test.com";
        registerAndGetToken(email);
        User managedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String adminToken = createAdminAndGetToken("admin-lock" + System.currentTimeMillis() + "@test.com");

        mockMvc.perform(get("/api/admin/users")
                        .param("search", email)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value(email));

        mockMvc.perform(patch("/api/admin/users/" + managedUser.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + managedUser.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void adminDashboardMetricsIncludeRealDocumentSubjectAndReportData() throws Exception {
        String ownerToken = registerAndGetToken("metrics-owner" + System.currentTimeMillis() + "@test.com");
        String reporterToken = registerAndGetToken("metrics-reporter" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("metrics-admin" + System.currentTimeMillis() + "@test.com");

        Integer subjectId = createSubject(ownerToken, "Metrics Subject");
        Integer privateDocumentId = uploadTextDocument(ownerToken, "Private Metrics Doc", "private metrics");
        Integer publicDocumentId = uploadTextDocument(ownerToken, "Public Metrics Doc", "public metrics");

        mockMvc.perform(patch("/api/documents/" + publicDocumentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/documents/" + privateDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":" + subjectId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/" + publicDocumentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Metrics report\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/dashboard/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents.totalDocuments").value(2))
                .andExpect(jsonPath("$.data.documents.publicDocuments").value(1))
                .andExpect(jsonPath("$.data.documents.privateDocuments").value(1))
                .andExpect(jsonPath("$.data.documents.moderatedDocuments").value(0))
                .andExpect(jsonPath("$.data.subjects.totalSubjects").value(1))
                .andExpect(jsonPath("$.data.reports.totalReports").value(1))
                .andExpect(jsonPath("$.data.reports.pendingReports").value(1))
                .andExpect(jsonPath("$.data.chatbotApiCalls").value(0));
    }

    @Test
    void adminCanListDetailAndModerateDocuments() throws Exception {
        String ownerEmail = "moderation-owner" + System.currentTimeMillis() + "@test.com";
        String ownerToken = registerAndGetToken(ownerEmail);
        String viewerToken = registerAndGetToken("moderation-viewer" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("moderation-admin" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Admin Moderation Notes", "moderation content");

        mockMvc.perform(patch("/api/documents/" + documentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/uploads/documents/")));

        mockMvc.perform(get("/api/admin/documents")
                        .param("keyword", "Moderation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(documentId))
                .andExpect(jsonPath("$.data.content[0].userEmail").value(ownerEmail));

        mockMvc.perform(get("/api/admin/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(documentId))
                .andExpect(jsonPath("$.data.status").value("PUBLIC"));

        mockMvc.perform(patch("/api/admin/documents/" + documentId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanViewReportDetail() throws Exception {
        String ownerToken = registerAndGetToken("report-detail-owner" + System.currentTimeMillis() + "@test.com");
        String reporterToken = registerAndGetToken("report-detail-user" + System.currentTimeMillis() + "@test.com");
        String adminToken = createAdminAndGetToken("report-detail-admin" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Reported Detail Notes", "reported content");

        mockMvc.perform(patch("/api/documents/" + documentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        MvcResult reportResult = mockMvc.perform(post("/api/documents/" + documentId + "/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"description\":\"Needs admin review\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Integer reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/admin/reports/" + reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.reason").value("OTHER"));
    }

    private String registerAndGetToken(String email) throws Exception {
        String registerJson = """
                {"email":"%s","password":"secret123","fullName":"Regular User"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Integer createSubject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private Integer uploadTextDocument(String token, String title, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "admin-metrics.txt",
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

    private String createAdminAndGetToken(String email) throws Exception {
        User admin = new User();
        admin.setEmail(email);
        admin.setFullName("Admin User");
        admin.setPasswordHash(passwordEncoder.encode("Admin12345"));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        String loginJson = """
                {"email":"%s","password":"Admin12345"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
