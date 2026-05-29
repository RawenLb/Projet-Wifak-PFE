package com.example.bctbackend.controller;

import com.example.bctbackend.dto.CreateUserRequest;
import com.example.bctbackend.dto.RoleDTO;
import com.example.bctbackend.dto.UserDTO;
import com.example.bctbackend.entities.User;
import com.example.bctbackend.service.KeycloakAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@ActiveProfiles("test")
@DisplayName("AdminController — Tests d'intégration")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean KeycloakAdminService keycloakAdminService;

    private UserDTO userDTO;
    private RoleDTO roleDTO;

    @BeforeEach
    void setUp() {
        userDTO = new UserDTO();
        userDTO.setId("user-1");
        userDTO.setUsername("testuser");
        userDTO.setEmail("test@test.com");
        userDTO.setFirstName("Test");
        userDTO.setLastName("User");
        userDTO.setEnabled(true);
        userDTO.setRoles(List.of("ROLE_AGENT"));

        roleDTO = new RoleDTO();
        roleDTO.setId("role-1");
        roleDTO.setName("ROLE_AGENT");
    }
    // GET /api/admin/users
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users — retourne la liste des utilisateurs")
    void getAllUsers_ok() throws Exception {
        when(keycloakAdminService.getAllUsers()).thenReturn(List.of(userDTO));

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users — erreur service → 500")
    void getAllUsers_serviceError_returns500() throws Exception {
        when(keycloakAdminService.getAllUsers()).thenThrow(new RuntimeException("Keycloak down"));

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/admin/users/{userId}
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/{id} — retourne l'utilisateur")
    void getUserById_ok() throws Exception {
        when(keycloakAdminService.getUserById("user-1")).thenReturn(userDTO);

        mockMvc.perform(get("/api/admin/users/user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("user-1"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/{id} — utilisateur introuvable → 404")
    void getUserById_notFound() throws Exception {
        when(keycloakAdminService.getUserById("unknown"))
            .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/api/admin/users/unknown"))
            .andExpect(status().isNotFound());
    }
    // GET /api/admin/users/search
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/search — retourne les résultats")
    void searchUsers_ok() throws Exception {
        when(keycloakAdminService.searchUsers("test")).thenReturn(List.of(userDTO));

        mockMvc.perform(get("/api/admin/users/search").param("query", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/search — erreur service → 500")
    void searchUsers_serviceError_returns500() throws Exception {
        when(keycloakAdminService.searchUsers(anyString()))
            .thenThrow(new RuntimeException("Search failed"));

        mockMvc.perform(get("/api/admin/users/search").param("query", "test"))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/admin/users
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users — crée un utilisateur → 201")
    void createUser_ok() throws Exception {
        when(keycloakAdminService.createUser(any(CreateUserRequest.class))).thenReturn("new-user-id");

        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setFirstName("New");
        req.setLastName("User");
        req.setEnabled(true);
        req.setRoles(List.of("ROLE_AGENT"));

        mockMvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value("new-user-id"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users — validation échoue → 400")
    void createUser_validationError_returns400() throws Exception {
        when(keycloakAdminService.createUser(any()))
            .thenThrow(new IllegalArgumentException("Username already exists"));

        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("existing");
        req.setEmail("existing@test.com");

        mockMvc.perform(post("/api/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }
    // PUT /api/admin/users/{userId}
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("PUT /users/{id} — met à jour l'utilisateur → 200")
    void updateUser_ok() throws Exception {
        doNothing().when(keycloakAdminService).updateUser(eq("user-1"), any(UserDTO.class));

        mockMvc.perform(put("/api/admin/users/user-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("User updated successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("PUT /users/{id} — erreur → 400")
    void updateUser_error_returns400() throws Exception {
        doThrow(new RuntimeException("Update failed"))
            .when(keycloakAdminService).updateUser(anyString(), any());

        mockMvc.perform(put("/api/admin/users/user-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
            .andExpect(status().isBadRequest());
    }
    // DELETE /api/admin/users/{userId}
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("DELETE /users/{id} — supprime l'utilisateur → 200")
    void deleteUser_ok() throws Exception {
        doNothing().when(keycloakAdminService).deleteUser("user-1");

        mockMvc.perform(delete("/api/admin/users/user-1").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("User deleted successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("DELETE /users/{id} — erreur → 400")
    void deleteUser_error_returns400() throws Exception {
        doThrow(new RuntimeException("Delete failed"))
            .when(keycloakAdminService).deleteUser(anyString());

        mockMvc.perform(delete("/api/admin/users/user-1").with(csrf()))
            .andExpect(status().isBadRequest());
    }
    // PATCH /api/admin/users/{userId}/status
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("PATCH /users/{id}/status — active l'utilisateur → 200")
    void toggleUserStatus_ok() throws Exception {
        doNothing().when(keycloakAdminService).toggleUserStatus("user-1", true);

        mockMvc.perform(patch("/api/admin/users/user-1/status")
                .with(csrf())
                .param("enabled", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("User status updated successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("PATCH /users/{id}/status — erreur → 400")
    void toggleUserStatus_error_returns400() throws Exception {
        doThrow(new RuntimeException("Toggle failed"))
            .when(keycloakAdminService).toggleUserStatus(anyString(), anyBoolean());

        mockMvc.perform(patch("/api/admin/users/user-1/status")
                .with(csrf())
                .param("enabled", "false"))
            .andExpect(status().isBadRequest());
    }
    // POST /api/admin/users/{userId}/reset-password
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users/{id}/reset-password — envoie l'email → 200")
    void resetPassword_ok() throws Exception {
        doNothing().when(keycloakAdminService).sendPasswordResetEmail("user-1");

        mockMvc.perform(post("/api/admin/users/user-1/reset-password").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password reset email sent successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users/{id}/reset-password — erreur → 400")
    void resetPassword_error_returns400() throws Exception {
        doThrow(new RuntimeException("Email failed"))
            .when(keycloakAdminService).sendPasswordResetEmail(anyString());

        mockMvc.perform(post("/api/admin/users/user-1/reset-password").with(csrf()))
            .andExpect(status().isBadRequest());
    }
    // GET /api/admin/roles
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /roles — retourne tous les rôles")
    void getAllRoles_ok() throws Exception {
        when(keycloakAdminService.getAllRoles()).thenReturn(List.of(roleDTO));

        mockMvc.perform(get("/api/admin/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("ROLE_AGENT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /roles — erreur → 500")
    void getAllRoles_error_returns500() throws Exception {
        when(keycloakAdminService.getAllRoles()).thenThrow(new RuntimeException("Roles error"));

        mockMvc.perform(get("/api/admin/roles"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/admin/users/{userId}/roles
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/{id}/roles — retourne les rôles de l'utilisateur")
    void getUserRoles_ok() throws Exception {
        when(keycloakAdminService.getUserRoles("user-1")).thenReturn(List.of(roleDTO));

        mockMvc.perform(get("/api/admin/users/user-1/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("ROLE_AGENT"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /users/{id}/roles — erreur → 500")
    void getUserRoles_error_returns500() throws Exception {
        when(keycloakAdminService.getUserRoles(anyString()))
            .thenThrow(new RuntimeException("Roles error"));

        mockMvc.perform(get("/api/admin/users/user-1/roles"))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/admin/users/{userId}/roles
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users/{id}/roles — assigne les rôles → 200")
    void assignRoles_ok() throws Exception {
        doNothing().when(keycloakAdminService).assignRoles(eq("user-1"), anyList());

        mockMvc.perform(post("/api/admin/users/user-1/roles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"ROLE_AGENT\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Roles assigned successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /users/{id}/roles — erreur → 400")
    void assignRoles_error_returns400() throws Exception {
        doThrow(new RuntimeException("Assign failed"))
            .when(keycloakAdminService).assignRoles(anyString(), anyList());

        mockMvc.perform(post("/api/admin/users/user-1/roles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"ROLE_AGENT\"]"))
            .andExpect(status().isBadRequest());
    }
    // DELETE /api/admin/users/{userId}/roles
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("DELETE /users/{id}/roles — retire les rôles → 200")
    void removeRoles_ok() throws Exception {
        doNothing().when(keycloakAdminService).removeRoles(eq("user-1"), anyList());

        mockMvc.perform(delete("/api/admin/users/user-1/roles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"ROLE_AGENT\"]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Roles removed successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("DELETE /users/{id}/roles — erreur → 400")
    void removeRoles_error_returns400() throws Exception {
        doThrow(new RuntimeException("Remove failed"))
            .when(keycloakAdminService).removeRoles(anyString(), anyList());

        mockMvc.perform(delete("/api/admin/users/user-1/roles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"ROLE_AGENT\"]"))
            .andExpect(status().isBadRequest());
    }
    // GET /api/admin/roles/{roleName}/users
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /roles/{roleName}/users — retourne les utilisateurs du rôle")
    void getUsersByRole_ok() throws Exception {
        when(keycloakAdminService.getUsersByRole("ROLE_AGENT")).thenReturn(List.of(userDTO));

        mockMvc.perform(get("/api/admin/roles/ROLE_AGENT/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /roles/{roleName}/users — erreur → 500")
    void getUsersByRole_error_returns500() throws Exception {
        when(keycloakAdminService.getUsersByRole(anyString()))
            .thenThrow(new RuntimeException("Role error"));

        mockMvc.perform(get("/api/admin/roles/ROLE_AGENT/users"))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/admin/sync/all-users
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /sync/all-users — synchronise tous les utilisateurs → 200")
    void syncAllUsers_ok() throws Exception {
        doNothing().when(keycloakAdminService).syncAllUsersToMySQL();

        mockMvc.perform(post("/api/admin/sync/all-users").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("All users synchronized to MySQL successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /sync/all-users — erreur → 500")
    void syncAllUsers_error_returns500() throws Exception {
        doThrow(new RuntimeException("Sync failed"))
            .when(keycloakAdminService).syncAllUsersToMySQL();

        mockMvc.perform(post("/api/admin/sync/all-users").with(csrf()))
            .andExpect(status().isInternalServerError());
    }
    // POST /api/admin/sync/user/{userId}
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /sync/user/{id} — synchronise un utilisateur → 200")
    void syncUser_ok() throws Exception {
        doNothing().when(keycloakAdminService).syncUserToMySQL("user-1");

        mockMvc.perform(post("/api/admin/sync/user/user-1").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("User synchronized to MySQL successfully"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /sync/user/{id} — erreur → 500")
    void syncUser_error_returns500() throws Exception {
        doThrow(new RuntimeException("Sync failed"))
            .when(keycloakAdminService).syncUserToMySQL(anyString());

        mockMvc.perform(post("/api/admin/sync/user/user-1").with(csrf()))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/admin/mysql/users
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /mysql/users — retourne les utilisateurs MySQL → 200")
    void getMySQLUsers_ok() throws Exception {
        User user = new User();
        user.setKeycloakId("kc-1");
        user.setUsername("testuser");
        when(keycloakAdminService.getAllMySQLUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/mysql/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /mysql/users — erreur → 500")
    void getMySQLUsers_error_returns500() throws Exception {
        when(keycloakAdminService.getAllMySQLUsers())
            .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/admin/mysql/users"))
            .andExpect(status().isInternalServerError());
    }
    // GET /api/admin/mysql/users/{keycloakId}
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /mysql/users/{id} — retourne l'utilisateur MySQL → 200")
    void getMySQLUser_ok() throws Exception {
        User user = new User();
        user.setKeycloakId("kc-1");
        user.setUsername("testuser");
        when(keycloakAdminService.getMySQLUser("kc-1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/admin/mysql/users/kc-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /mysql/users/{id} — utilisateur absent → 404")
    void getMySQLUser_notFound() throws Exception {
        when(keycloakAdminService.getMySQLUser("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/mysql/users/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /mysql/users/{id} — erreur → 500")
    void getMySQLUser_error_returns500() throws Exception {
        when(keycloakAdminService.getMySQLUser(anyString()))
            .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/admin/mysql/users/kc-1"))
            .andExpect(status().isInternalServerError());
    }
}
