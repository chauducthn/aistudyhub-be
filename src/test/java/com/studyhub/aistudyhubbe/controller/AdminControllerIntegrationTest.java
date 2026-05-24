package com.studyhub.aistudyhubbe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
