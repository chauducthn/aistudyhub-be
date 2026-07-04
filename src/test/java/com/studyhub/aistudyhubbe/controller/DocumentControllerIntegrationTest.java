package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userCanUploadListAndViewOwnDocument() throws Exception {
        String token = registerAndGetToken("document" + System.currentTimeMillis() + "@test.com");
        Integer subjectId = createSubject(token, "Software Engineering");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture-notes.pdf",
                "application/pdf",
                "pdf-content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Lecture Notes")
                        .param("description", "Week 1 overview")
                        .param("subjectId", String.valueOf(subjectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Lecture Notes"))
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andExpect(jsonPath("$.data.fileType").value("PDF"))
                .andExpect(jsonPath("$.data.status").value("PRIVATE"))
                .andExpect(jsonPath("$.data.fileUrl").value(org.hamcrest.Matchers.startsWith("/uploads/documents/")))
                .andReturn();

        Integer documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/documents")
                        .param("keyword", "lecture")
                        .param("subjectId", String.valueOf(subjectId))
                        .param("status", "PRIVATE")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(documentId))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(documentId))
                .andExpect(jsonPath("$.data.originalFilename").value("lecture-notes.pdf"));
    }

    @Test
    void userCanUploadExcelDocument() throws Exception {
        String token = registerAndGetToken("excel-document" + System.currentTimeMillis() + "@test.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "study-plan.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "excel-content".getBytes());

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Study Plan")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileType").value("XLSX"))
                .andExpect(jsonPath("$.data.originalFilename").value("study-plan.xlsx"));
    }

    @Test
    void textDocumentUploadExtractsReadableText() throws Exception {
        String token = registerAndGetToken("extract-document" + System.currentTimeMillis() + "@test.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "extract-notes.txt",
                "text/plain",
                "Design patterns improve reusable software design.".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Extract Notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extractionStatus").value("EXTRACTED"))
                .andExpect(jsonPath("$.data.extractionError").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.extractedAt").exists())
                .andReturn();

        Integer documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extractionStatus").value("EXTRACTED"));
    }

    @Test
    void invalidDocumentTypeIsRejected() throws Exception {
        String token = registerAndGetToken("invalid-document" + System.currentTimeMillis() + "@test.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "bad-content".getBytes());

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Invalid File")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void userCannotUploadDocumentIntoAnotherUsersSubject() throws Exception {
        String ownerToken = registerAndGetToken("subject-owner-doc" + System.currentTimeMillis() + "@test.com");
        String otherToken = registerAndGetToken("subject-other-doc" + System.currentTimeMillis() + "@test.com");
        Integer subjectId = createSubject(ownerToken, "Owner Subject");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "private notes".getBytes());

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Other User Notes")
                        .param("subjectId", String.valueOf(subjectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanUpdateVisibilityDownloadAndSoftDeleteDocument() throws Exception {
        String ownerToken = registerAndGetToken("document-owner" + System.currentTimeMillis() + "@test.com");
        String otherToken = registerAndGetToken("document-other" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Private Notes", "downloadable content");

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Notes\",\"description\":\"Updated description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Notes"))
                .andExpect(jsonPath("$.data.description").value("Updated description"));

        mockMvc.perform(patch("/api/documents/" + documentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLIC"));

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("downloadable content"));

        mockMvc.perform(delete("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void documentFilesAreOnlyAccessibleThroughDownloadEndpoint() throws Exception {
        String token = registerAndGetToken("document-static-denied" + System.currentTimeMillis() + "@test.com");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "secure-notes.txt",
                "text/plain",
                "secure content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Secure Notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        Integer documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.id");
        String fileUrl = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.fileUrl");

        mockMvc.perform(get("/api/documents/" + documentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("secure content"));

        mockMvc.perform(get(fileUrl))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/" + documentId + "/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanFindAnotherUsersPublicDocuments() throws Exception {
        String ownerToken = registerAndGetToken("public-owner" + System.currentTimeMillis() + "@test.com");
        String viewerToken = registerAndGetToken("public-viewer" + System.currentTimeMillis() + "@test.com");
        Integer publicDocumentId = uploadTextDocument(ownerToken, "Shared Algorithms Notes", "public content");
        Integer privateDocumentId = uploadTextDocument(ownerToken, "Private Algorithms Notes", "private content");

        mockMvc.perform(patch("/api/documents/" + publicDocumentId + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/public")
                        .param("keyword", "Shared Algorithms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(publicDocumentId))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLIC"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/documents/public/" + publicDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(publicDocumentId));

        mockMvc.perform(get("/api/documents/public/" + privateDocumentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCanMoveDocumentBackToUncategorizedStorageBeforeDeletingSubject() throws Exception {
        String token = registerAndGetToken("uncategorized" + System.currentTimeMillis() + "@test.com");
        Integer subjectId = createSubject(token, "Temporary Subject");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "temporary-notes.txt",
                "text/plain",
                "temporary notes".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Temporary Notes")
                        .param("subjectId", String.valueOf(subjectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(subjectId))
                .andReturn();

        Integer documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/documents/" + documentId + "/subject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectId").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(delete("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotUpdateOrDeleteAnotherUsersDocument() throws Exception {
        String ownerToken = registerAndGetToken("document-owner-denied" + System.currentTimeMillis() + "@test.com");
        String otherToken = registerAndGetToken("document-other-denied" + System.currentTimeMillis() + "@test.com");
        Integer documentId = uploadTextDocument(ownerToken, "Owner Notes", "private content");

        mockMvc.perform(patch("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stolen Notes\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
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
                "notes.txt",
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

    private String registerAndGetToken(String email) throws Exception {
        String registerJson = """
                {"email":"%s","password":"secret123","fullName":"Document User"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
