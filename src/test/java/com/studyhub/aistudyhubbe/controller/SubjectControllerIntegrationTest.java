package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userCanCreateListUpdateAndDeleteSubject() throws Exception {
        String token = registerAndGetToken("subject" + System.currentTimeMillis() + "@test.com");

        MvcResult createResult = mockMvc.perform(post("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Software Engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Software Engineering"))
                .andReturn();

        Integer subjectId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(subjectId));

        mockMvc.perform(patch("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Database Systems\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Database Systems"));

        mockMvc.perform(delete("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void duplicateSubjectNameIsRejectedForSameUser() throws Exception {
        String token = registerAndGetToken("duplicate-subject" + System.currentTimeMillis() + "@test.com");

        mockMvc.perform(post("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algorithms\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" algorithms \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void userCannotUpdateOrDeleteAnotherUsersSubject() throws Exception {
        String ownerToken = registerAndGetToken("subject-owner" + System.currentTimeMillis() + "@test.com");
        String otherToken = registerAndGetToken("subject-other" + System.currentTimeMillis() + "@test.com");

        MvcResult createResult = mockMvc.perform(post("/api/subjects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private Subject\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Integer subjectId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(patch("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stolen Subject\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/subjects/" + subjectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String email) throws Exception {
        String registerJson = """
                {"email":"%s","password":"secret123","fullName":"Subject User"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
