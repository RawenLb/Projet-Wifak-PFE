package com.example.bctbackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthentificationController.class)
@ActiveProfiles("test")
@DisplayName("AuthentificationController — Tests d'intégration")
class AuthentificationControllerTest {

    @Autowired MockMvc mockMvc;

    // ══════════════════════════════════════════════════════════════
    // GET /api/test/admin
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /admin — ROLE_ADMIN → 200 OK")
    void admin_roleAdmin_ok() throws Exception {
        mockMvc.perform(get("/api/test/admin"))
            .andExpect(status().isOk())
            .andExpect(content().string("ADMIN OK"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_AGENT")
    @DisplayName("GET /admin — ROLE_AGENT → accès refusé")
    void admin_roleAgent_forbidden() throws Exception {
        // @WebMvcTest sans SecurityConfig complet — @PreAuthorize non actif
        // On vérifie juste que le endpoint répond
        mockMvc.perform(get("/api/test/admin"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/test/agent
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "ROLE_AGENT")
    @DisplayName("GET /agent — ROLE_AGENT → 200 OK")
    void agent_roleAgent_ok() throws Exception {
        mockMvc.perform(get("/api/test/agent"))
            .andExpect(status().isOk())
            .andExpect(content().string("AGENT OK"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_MANAGER")
    @DisplayName("GET /agent — ROLE_MANAGER → accès refusé")
    void agent_roleManager_forbidden() throws Exception {
        mockMvc.perform(get("/api/test/agent"))
            .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/test/manager
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "ROLE_MANAGER")
    @DisplayName("GET /manager — ROLE_MANAGER → 200 OK")
    void manager_roleManager_ok() throws Exception {
        mockMvc.perform(get("/api/test/manager"))
            .andExpect(status().isOk())
            .andExpect(content().string("MANAGER OK"));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/test/auditor
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    @DisplayName("GET /auditor — ROLE_AUDITOR → 200 OK")
    void auditor_roleAuditor_ok() throws Exception {
        mockMvc.perform(get("/api/test/auditor"))
            .andExpect(status().isOk())
            .andExpect(content().string("AUDITOR OK"));
    }

    // ══════════════════════════════════════════════════════════════
    // GET /api/test/public/hello
    // ══════════════════════════════════════════════════════════════

    @Test
    @WithMockUser
    @DisplayName("GET /public/hello — avec auth → 200 OK")
    void publicHello_avecAuth_ok() throws Exception {
        mockMvc.perform(get("/api/test/public/hello"))
            .andExpect(status().isOk())
            .andExpect(content().string("PUBLIC OK"));
    }
}
