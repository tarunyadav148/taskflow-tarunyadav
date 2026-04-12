package com.taskflow.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests against a real Postgres instance (via Testcontainers).
 * Tests are ordered because later tests depend on data created by earlier ones
 * (e.g. we register a user, then use that token for everything else).
 *
 * Not ideal for isolation, but pragmatic for a take-home.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // shared state across ordered tests
    static String authToken;
    static String testProjectId;
    static String testTaskId;

    // ─── auth ───────────────────────────────────────────────

    @Test
    @Order(1)
    void shouldRegisterNewUser() throws Exception {
        MvcResult result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Tarun","email":"tarun@test.com","password":"pass1234"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Tarun"))
                .andExpect(jsonPath("$.user.email").value("tarun@test.com"))
                .andReturn();

        authToken = json.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    @Test
    @Order(2)
    void shouldRejectDuplicateEmail() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Dup","email":"tarun@test.com","password":"pass1234"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already in use"));
    }

    @Test
    @Order(3)
    void shouldLoginWithCorrectCredentials() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"tarun@test.com","password":"pass1234"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("tarun@test.com"));
    }

    @Test
    @Order(4)
    void shouldRejectWrongPassword() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"tarun@test.com","password":"wrongwrong"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void shouldReturn401WithoutToken() throws Exception {
        mvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    // ─── project + task CRUD ────────────────────────────────

    @Test
    @Order(10)
    void shouldCreateProjectAndTaskThenUpdateTaskStatus() throws Exception {
        // create project
        MvcResult projResult = mvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"My Project"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Project"))
                .andReturn();

        testProjectId = json.readTree(projResult.getResponse().getContentAsString())
                .get("id").asText();

        // create task in that project
        MvcResult taskResult = mvc.perform(post("/projects/" + testProjectId + "/tasks")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"First task","priority":"high"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("First task"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andReturn();

        testTaskId = json.readTree(taskResult.getResponse().getContentAsString())
                .get("id").asText();

        // move it to done
        mvc.perform(patch("/tasks/" + testTaskId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"status":"done"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    @Order(11)
    void shouldPaginateAndFilterTasks() throws Exception {
        // add another task so we have 2 total
        mvc.perform(post("/projects/" + testProjectId + "/tasks")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Second task","priority":"low"}
                            """))
                .andExpect(status().isCreated());

        // page 1 with limit 1 — should get 1 item but total=2
        mvc.perform(get("/projects/" + testProjectId + "/tasks?page=1&limit=1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        // filter by status=todo — only the second task
        mvc.perform(get("/projects/" + testProjectId + "/tasks?status=todo")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("todo"));
    }

    @Test
    @Order(12)
    void shouldReturnProjectStats() throws Exception {
        mvc.perform(get("/projects/" + testProjectId + "/stats")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.byStatus.done").value(1))
                .andExpect(jsonPath("$.byStatus.todo").value(1))
                .andExpect(jsonPath("$.byAssignee.Unassigned").value(2));
    }

    @Test
    @Order(13)
    void shouldDeleteTaskAndReturn404AfterDeletion() throws Exception {
        mvc.perform(delete("/tasks/" + testTaskId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        // trying to update a deleted task should 404
        mvc.perform(patch("/tasks/" + testTaskId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"status":"todo"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void shouldRejectInvalidEnumValues() throws Exception {
        // create a fresh task to test against
        MvcResult r = mvc.perform(post("/projects/" + testProjectId + "/tasks")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Validation test","priority":"medium"}
                            """))
                .andExpect(status().isCreated())
                .andReturn();

        String tid = json.readTree(r.getResponse().getContentAsString()).get("id").asText();

        // try to set bogus status and priority
        mvc.perform(patch("/tasks/" + tid)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"status":"banana","priority":"nope"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation failed"))
                .andExpect(jsonPath("$.fields.status").exists())
                .andExpect(jsonPath("$.fields.priority").exists());
    }
}
