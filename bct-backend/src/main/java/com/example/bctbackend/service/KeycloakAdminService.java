package com.example.bctbackend.service;

import com.example.bctbackend.dto.CreateUserRequest;
import com.example.bctbackend.dto.RoleDTO;
import com.example.bctbackend.dto.UserDTO;
import com.example.bctbackend.entities.User;
import com.example.bctbackend.repositories.UserRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final Keycloak keycloak;
    private final UserRepository userRepository;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakAdminService(Keycloak keycloak, UserRepository userRepository) {
        this.keycloak = keycloak;
        this.userRepository = userRepository;
    }

    private RealmResource getRealmResource() {
        return keycloak.realm(realm);
    }

    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    // ========== USER MANAGEMENT WITH MYSQL SYNC ==========

    /**
     * Get all users with their roles
     */
    public List<UserDTO> getAllUsers() {
        List<UserRepresentation> keycloakUsers = getUsersResource().list();

        return keycloakUsers.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get user by ID
     */
    public UserDTO getUserById(String userId) {
        UserRepresentation user = getUsersResource().get(userId).toRepresentation();
        return convertToUserDTO(user);
    }

    /**
     * Search users by username or email
     */
    public List<UserDTO> searchUsers(String search) {
        List<UserRepresentation> users = getUsersResource().search(search, 0, 100);

        return users.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create new user and sync to MySQL
     */
    @Transactional
    public String createUser(CreateUserRequest request) {
        // ✅ VALIDATION DES DONNÉES
        validateCreateUserRequest(request);

        log.info("🔄 Creating user in Keycloak: {} ({})", request.getUsername(), request.getEmail());

        // 1. Create user in Keycloak
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(request.getUsername());
        kcUser.setEmail(request.getEmail());
        kcUser.setFirstName(request.getFirstName() != null && !request.getFirstName().isEmpty()
                ? request.getFirstName() : "");
        kcUser.setLastName(request.getLastName() != null && !request.getLastName().isEmpty()
                ? request.getLastName() : "");
        kcUser.setEnabled(request.isEnabled());
        kcUser.setEmailVerified(true);

        Response response = null;
        try {
            response = getUsersResource().create(kcUser);

            // ✅ VÉRIFICATION DÉTAILLÉE DU STATUT
            if (response.getStatus() != 201) {
                String errorBody = "";
                if (response.hasEntity()) {
                    try {
                        errorBody = response.readEntity(String.class);
                    } catch (Exception e) {
                        errorBody = "Unable to read error details";
                    }
                }

                log.error("❌ Keycloak returned status {}: {}", response.getStatus(), errorBody);
                throw new RuntimeException(
                        String.format("Failed to create user in Keycloak (Status %d): %s",
                                response.getStatus(), errorBody)
                );
            }

            // Get created user ID from location header
            String locationHeader = response.getHeaderString("Location");
            if (locationHeader == null || locationHeader.isEmpty()) {
                throw new RuntimeException("No Location header in Keycloak response");
            }

            String userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
            log.info("✅ User created in Keycloak with ID: {}", userId);

            // 2. Set password
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                try {
                    CredentialRepresentation credential = new CredentialRepresentation();
                    credential.setType(CredentialRepresentation.PASSWORD);
                    credential.setValue(request.getPassword());
                    credential.setTemporary(false);
                    getUsersResource().get(userId).resetPassword(credential);
                    log.info("✅ Password set for user: {}", userId);
                } catch (Exception e) {
                    log.error("❌ Failed to set password: {}", e.getMessage());
                    // Continue even if password setting fails
                }
            }

            // 3. Assign roles in Keycloak
            if (request.getRoles() != null && !request.getRoles().isEmpty()) {
                try {
                    assignRoles(userId, request.getRoles());
                    log.info("✅ Roles assigned in Keycloak: {}", request.getRoles());
                } catch (Exception e) {
                    log.error("❌ Failed to assign roles: {}", e.getMessage());
                    // Continue even if role assignment fails
                }
            }

            // 4. Sync to MySQL
            try {
                syncUserToMySQL(userId);
                log.info("✅ User synced to MySQL: {}", userId);
            } catch (Exception e) {
                log.error("❌ Failed to sync to MySQL: {}", e.getMessage());
                // Continue even if MySQL sync fails
            }

            return userId;

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Valide les données avant création
     */
    private void validateCreateUserRequest(CreateUserRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            errors.add("Username is required");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            errors.add("Email is required");
        } else if (!isValidEmail(request.getEmail())) {
            errors.add("Invalid email format");
        }

        // Vérifier si l'username existe déjà
        try {
            List<UserRepresentation> existingUsers = getUsersResource()
                    .search(request.getUsername(), true);
            if (!existingUsers.isEmpty()) {
                errors.add("Username already exists");
            }
        } catch (Exception e) {
            log.warn("Could not check username uniqueness: {}", e.getMessage());
        }

        // Vérifier si l'email existe déjà
        try {
            List<UserRepresentation> existingUsers = getUsersResource()
                    .search(request.getEmail(), null, null, null, 0, 10);
            for (UserRepresentation user : existingUsers) {
                if (user.getEmail() != null && request.getEmail().equalsIgnoreCase(user.getEmail())) {
                    errors.add("Email already exists");
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Could not check email uniqueness: {}", e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }

    /**
     * Validation simple d'email
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    /**
     * Update user in both Keycloak and MySQL
     */
    @Transactional
    public void updateUser(String userId, UserDTO userDTO) {
        log.info("🔄 Updating user in Keycloak: {}", userId);

        // 1. Update in Keycloak
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation kcUser = userResource.toRepresentation();

        kcUser.setEmail(userDTO.getEmail());
        kcUser.setFirstName(userDTO.getFirstName());
        kcUser.setLastName(userDTO.getLastName());
        kcUser.setEnabled(userDTO.isEnabled());

        userResource.update(kcUser);
        log.info("✅ User updated in Keycloak: {}", userId);

        // 2. Sync to MySQL
        syncUserToMySQL(userId);
        log.info("✅ User synced to MySQL: {}", userId);
    }

    /**
     * Delete user from both Keycloak and MySQL
     */
    @Transactional
    public void deleteUser(String userId) {
        log.info("🔄 Deleting user: {}", userId);

        // 1. Delete from MySQL first
        if (userRepository.existsByKeycloakId(userId)) {
            userRepository.deleteById(userId);
            log.info("✅ User deleted from MySQL: {}", userId);
        }

        // 2. Delete from Keycloak
        getUsersResource().get(userId).remove();
        log.info("✅ User deleted from Keycloak: {}", userId);
    }

    /**
     * Enable/Disable user in both systems
     */
    @Transactional
    public void toggleUserStatus(String userId, boolean enabled) {
        log.info("🔄 Toggling user status: {} -> {}", userId, enabled);

        // 1. Update in Keycloak
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
        log.info("✅ User status updated in Keycloak: {}", userId);

        // 2. Sync to MySQL
        syncUserToMySQL(userId);
        log.info("✅ User status synced to MySQL: {}", userId);
    }

    /**
     * Reset user password (send email)
     */
    public void sendPasswordResetEmail(String userId) {
        getUsersResource().get(userId)
                .executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
    }
    // ========== ROLE MANAGEMENT WITH MYSQL SYNC ==========

    /**
     * Get all realm roles
     */
    public List<RoleDTO> getAllRoles() {
        List<RoleRepresentation> roles = getRealmResource().roles().list();

        return roles.stream()
                .filter(role -> role.getName().startsWith("ROLE_"))
                .map(this::convertToRoleDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get user's roles
     */
    public List<RoleDTO> getUserRoles(String userId) {
        List<RoleRepresentation> roles = getUsersResource()
                .get(userId)
                .roles()
                .realmLevel()
                .listEffective();

        return roles.stream()
                .filter(role -> role.getName().startsWith("ROLE_"))
                .map(this::convertToRoleDTO)
                .collect(Collectors.toList());
    }

    /**
     * Assign roles to user and sync to MySQL
     */
    @Transactional
    public void assignRoles(String userId, List<String> roleNames) {
        log.info("🔄 Assigning roles to user {}: {}", userId, roleNames);

        // 1. Assign in Keycloak
        List<RoleRepresentation> rolesToAdd = roleNames.stream()
                .map(roleName -> {
                    try {
                        return getRealmResource().roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role not found: {}", roleName);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!rolesToAdd.isEmpty()) {
            getUsersResource().get(userId).roles().realmLevel().add(rolesToAdd);
            log.info("✅ Roles assigned in Keycloak: {}", roleNames);
        }

        // 2. Sync to MySQL
        syncUserToMySQL(userId);
        log.info("✅ Roles synced to MySQL: {}", roleNames);
    }

    /**
     * Remove roles from user and sync to MySQL
     */
    @Transactional
    public void removeRoles(String userId, List<String> roleNames) {
        log.info("🔄 Removing roles from user {}: {}", userId, roleNames);

        // 1. Remove from Keycloak
        List<RoleRepresentation> rolesToRemove = roleNames.stream()
                .map(roleName -> {
                    try {
                        return getRealmResource().roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role not found: {}", roleName);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!rolesToRemove.isEmpty()) {
            getUsersResource().get(userId).roles().realmLevel().remove(rolesToRemove);
            log.info("✅ Roles removed from Keycloak: {}", roleNames);
        }

        // 2. Sync to MySQL
        syncUserToMySQL(userId);
        log.info("✅ Roles removal synced to MySQL: {}", roleNames);
    }

    /**
     * Get users by role
     */
    public List<UserDTO> getUsersByRole(String roleName) {
        List<UserRepresentation> users = new ArrayList<>(
                getRealmResource()
                        .roles()
                        .get(roleName)
                        .getRoleUserMembers()
        );

        return users.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get user from MySQL by keycloak ID
     */
    public Optional<User> getMySQLUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    /**
     * Get all users from MySQL
     */
    public List<User> getAllMySQLUsers() {
        return userRepository.findAll();
    }

    // ========== MYSQL SYNC HELPERS ==========

    /**
     * Synchronize a Keycloak user to MySQL database
     */
    @Transactional
    public void syncUserToMySQL(String keycloakUserId) {
        try {
            // Get user from Keycloak
            UserRepresentation kcUser = getUsersResource().get(keycloakUserId).toRepresentation();

            // Get user roles
            List<RoleRepresentation> kcRoles = getUsersResource()
                    .get(keycloakUserId)
                    .roles()
                    .realmLevel()
                    .listEffective();

            Set<String> roles = kcRoles.stream()
                    .filter(role -> role.getName().startsWith("ROLE_"))
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());

            // Check if user exists in MySQL
            User dbUser = userRepository.findByKeycloakId(keycloakUserId)
                    .orElse(new User());

            // Update/Create user entity
            dbUser.setKeycloakId(kcUser.getId());
            dbUser.setUsername(kcUser.getUsername());
            dbUser.setEmail(kcUser.getEmail());
            dbUser.setFirstName(kcUser.getFirstName());
            dbUser.setLastName(kcUser.getLastName());
            dbUser.setEnabled(kcUser.isEnabled());
            dbUser.setEmailVerified(kcUser.isEmailVerified() != null ? kcUser.isEmailVerified() : false);
            dbUser.setRoles(roles);

            // Convert timestamp if available
            if (kcUser.getCreatedTimestamp() != null && dbUser.getCreatedAt() == null) {
                dbUser.setCreatedAt(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(kcUser.getCreatedTimestamp()),
                                ZoneId.systemDefault()
                        )
                );
            }

            // Save to MySQL
            userRepository.save(dbUser);
            log.debug("✅ User synced to MySQL: {}", kcUser.getUsername());

        } catch (Exception e) {
            log.error("❌ Failed to sync user {} to MySQL: {}", keycloakUserId, e.getMessage());
            throw new RuntimeException("Failed to sync user to MySQL", e);
        }
    }

    /**
     * Synchronize all Keycloak users to MySQL (useful for initial setup)
     */
    @Transactional
    public void syncAllUsersToMySQL() {
        log.info("🔄 Starting full synchronization of Keycloak users to MySQL...");

        List<UserRepresentation> allUsers = getUsersResource().list();
        int successCount = 0;
        int errorCount = 0;

        for (UserRepresentation kcUser : allUsers) {
            try {
                syncUserToMySQL(kcUser.getId());
                successCount++;
            } catch (Exception e) {
                log.error("❌ Failed to sync user {}: {}", kcUser.getUsername(), e.getMessage());
                errorCount++;
            }
        }

        log.info("✅ Synchronization complete: {} success, {} errors", successCount, errorCount);
    }

    // ========== HELPER METHODS ==========

    private UserDTO convertToUserDTO(UserRepresentation user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEnabled(user.isEnabled());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setCreatedTimestamp(user.getCreatedTimestamp());

        // Get user roles
        try {
            List<String> roles = getUsersResource()
                    .get(user.getId())
                    .roles()
                    .realmLevel()
                    .listEffective()
                    .stream()
                    .filter(role -> role.getName().startsWith("ROLE_"))
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());

            dto.setRoles(roles);
        } catch (Exception e) {
            log.warn("Could not fetch roles for user {}: {}", user.getId(), e.getMessage());
            dto.setRoles(new ArrayList<>());
        }

        return dto;
    }

    private RoleDTO convertToRoleDTO(RoleRepresentation role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        return dto;
    }
}