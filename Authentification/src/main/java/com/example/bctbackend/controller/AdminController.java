package com.example.bctbackend.controller;

import com.example.bctbackend.dto.CreateUserRequest;
import com.example.bctbackend.dto.RoleDTO;
import com.example.bctbackend.dto.UserDTO;
import com.example.bctbackend.entities.User;
import com.example.bctbackend.service.KeycloakAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration", description = "Gestion des utilisateurs et des rôles Keycloak")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final KeycloakAdminService keycloakAdminService;

    public AdminController(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    // ========== USER MANAGEMENT ==========

    /**
     * Get all users
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.info("📋 Admin requesting all users");
        try {
            List<UserDTO> users = keycloakAdminService.getAllUsers();
            log.info("✅ Retrieved {} users", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("❌ Error getting users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user by ID — accessible aussi par les services internes (chat-service)
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_INTERNAL')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String userId) {
        log.info("🔍 Admin requesting user: {}", userId);
        try {
            UserDTO user = keycloakAdminService.getUserById(userId);
            log.info("✅ Retrieved user: {}", user.getUsername());
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("❌ Error getting user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Search users
     */
    @GetMapping("/users/search")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam String query) {
        log.info("🔍 Admin searching users: {}", query);
        try {
            List<UserDTO> users = keycloakAdminService.searchUsers(query);
            log.info("✅ Found {} users", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("❌ Error searching users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create new user
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, String>> createUser(@RequestBody CreateUserRequest request) {
        log.info("➕ Admin creating user: {}", request.getUsername());
        try {
            String userId = keycloakAdminService.createUser(request);
            log.info("✅ User created successfully: {}", userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("userId", userId, "message", "User created successfully"));
        } catch (Exception e) {
            log.error("❌ Error creating user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update user
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> updateUser(
            @PathVariable String userId,
            @RequestBody UserDTO userDTO) {
        log.info("✏️ Admin updating user: {}", userId);
        try {
            keycloakAdminService.updateUser(userId, userDTO);
            log.info("✅ User updated successfully: {}", userId);
            return ResponseEntity.ok(Map.of("message", "User updated successfully"));
        } catch (Exception e) {
            log.error("❌ Error updating user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete user
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String userId) {
        log.info("🗑️ Admin deleting user: {}", userId);
        try {
            keycloakAdminService.deleteUser(userId);
            log.info("✅ User deleted successfully: {}", userId);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            log.error("❌ Error deleting user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Toggle user status (enable/disable)
     */
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @PathVariable String userId,
            @RequestParam boolean enabled) {
        log.info("🔄 Admin toggling user status: {} -> {}", userId, enabled);
        try {
            keycloakAdminService.toggleUserStatus(userId, enabled);
            log.info("✅ User status updated: {} -> {}", userId, enabled);
            return ResponseEntity.ok(Map.of("message", "User status updated successfully"));
        } catch (Exception e) {
            log.error("❌ Error updating user status {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Send password reset email
     */
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String userId) {
        log.info("🔐 Admin requesting password reset for user: {}", userId);
        try {
            keycloakAdminService.sendPasswordResetEmail(userId);
            log.info("✅ Password reset email sent: {}", userId);
            return ResponseEntity.ok(Map.of("message", "Password reset email sent successfully"));
        } catch (Exception e) {
            log.error("❌ Error sending password reset {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ========== ROLE MANAGEMENT ==========

    /**
     * Get all roles
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        log.info("📋 Admin requesting all roles");
        try {
            List<RoleDTO> roles = keycloakAdminService.getAllRoles();
            log.info("✅ Retrieved {} roles", roles.size());
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            log.error("❌ Error getting roles: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user's roles
     */
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<List<RoleDTO>> getUserRoles(@PathVariable String userId) {
        log.info("🔍 Admin requesting roles for user: {}", userId);
        try {
            List<RoleDTO> roles = keycloakAdminService.getUserRoles(userId);
            log.info("✅ Retrieved {} roles for user {}", roles.size(), userId);
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            log.error("❌ Error getting roles for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Assign roles to user
     */
    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Map<String, String>> assignRoles(
            @PathVariable String userId,
            @RequestBody List<String> roleNames) {
        log.info("➕ Admin assigning roles to user {}: {}", userId, roleNames);
        try {
            keycloakAdminService.assignRoles(userId, roleNames);
            log.info("✅ Roles assigned successfully to user: {}", userId);
            return ResponseEntity.ok(Map.of("message", "Roles assigned successfully"));
        } catch (Exception e) {
            log.error("❌ Error assigning roles to user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove roles from user
     */
    @DeleteMapping("/users/{userId}/roles")
    public ResponseEntity<Map<String, String>> removeRoles(
            @PathVariable String userId,
            @RequestBody List<String> roleNames) {
        log.info("➖ Admin removing roles from user {}: {}", userId, roleNames);
        try {
            keycloakAdminService.removeRoles(userId, roleNames);
            log.info("✅ Roles removed successfully from user: {}", userId);
            return ResponseEntity.ok(Map.of("message", "Roles removed successfully"));
        } catch (Exception e) {
            log.error("❌ Error removing roles from user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get users by role — accessible aussi par les services internes (chat-service)
     */
    @GetMapping("/roles/{roleName}/users")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_INTERNAL')")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String roleName) {
        log.info("🔍 Admin requesting users with role: {}", roleName);
        try {
            List<UserDTO> users = keycloakAdminService.getUsersByRole(roleName);
            log.info("✅ Retrieved {} users with role {}", users.size(), roleName);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("❌ Error getting users by role {}: {}", roleName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ========== MYSQL SYNCHRONIZATION ENDPOINTS ==========

    /**
     * Synchronize all Keycloak users to MySQL
     * This is useful for initial setup or after bulk changes
     */
    @PostMapping("/sync/all-users")
    public ResponseEntity<Map<String, String>> syncAllUsers() {
        log.info("🔄 Admin requesting full user synchronization to MySQL");
        try {
            keycloakAdminService.syncAllUsersToMySQL();
            log.info("✅ All users synchronized successfully");
            return ResponseEntity.ok(Map.of("message", "All users synchronized to MySQL successfully"));
        } catch (Exception e) {
            log.error("❌ Error synchronizing users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Synchronize a specific user to MySQL
     */
    @PostMapping("/sync/user/{userId}")
    public ResponseEntity<Map<String, String>> syncUser(@PathVariable String userId) {
        log.info("🔄 Admin requesting sync for user: {}", userId);
        try {
            keycloakAdminService.syncUserToMySQL(userId);
            log.info("✅ User synchronized successfully: {}", userId);
            return ResponseEntity.ok(Map.of("message", "User synchronized to MySQL successfully"));
        } catch (Exception e) {
            log.error("❌ Error synchronizing user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all users from MySQL database
     */
    @GetMapping("/mysql/users")
    public ResponseEntity<List<User>> getMySQLUsers() {
        log.info("📋 Admin requesting MySQL users");
        try {
            List<User> users = keycloakAdminService.getAllMySQLUsers();
            log.info("✅ Retrieved {} users from MySQL", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("❌ Error getting MySQL users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get MySQL user by Keycloak ID
     */
    @GetMapping("/mysql/users/{keycloakId}")
    public ResponseEntity<User> getMySQLUser(@PathVariable String keycloakId) {
        log.info("🔍 Admin requesting MySQL user: {}", keycloakId);
        try {
            return keycloakAdminService.getMySQLUser(keycloakId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("❌ Error getting MySQL user {}: {}", keycloakId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}