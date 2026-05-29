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

    // Propriétés injectées depuis application.yml
    @Value("${app.frontend-client-id}")
    private String clientId;

    @Value("${app.cors.allowed-origins}")
    private String frontendUrl;

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

    public List<UserDTO> getAllUsers() {
        List<UserRepresentation> keycloakUsers = getUsersResource().list();
        return keycloakUsers.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(String userId) {
        UserRepresentation user = getUsersResource().get(userId).toRepresentation();
        return convertToUserDTO(user);
    }

    public List<UserDTO> searchUsers(String search) {
        List<UserRepresentation> users = getUsersResource().search(search, 0, 100);
        return users.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public String createUser(CreateUserRequest request) {
        validateCreateUserRequest(request);

        log.info("🔄 Creating user in Keycloak: {} ({})", request.getUsername(), request.getEmail());

        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(request.getUsername());
        kcUser.setEmail(request.getEmail());
        kcUser.setFirstName(request.getFirstName() != null ? request.getFirstName() : "");
        kcUser.setLastName(request.getLastName() != null ? request.getLastName() : "");
        kcUser.setEnabled(true);
        kcUser.setEmailVerified(false);
        kcUser.setRequiredActions(Arrays.asList("UPDATE_PASSWORD", "VERIFY_EMAIL"));

        Response response = null;
        try {
            response = getUsersResource().create(kcUser);

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

            String locationHeader = response.getHeaderString("Location");
            if (locationHeader == null || locationHeader.isEmpty()) {
                throw new RuntimeException("No Location header in Keycloak response");
            }
            String userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
            log.info("✅ User created in Keycloak with ID: {}", userId);

            if (request.getRoles() != null && !request.getRoles().isEmpty()) {
                try {
                    assignRoles(userId, request.getRoles());
                    log.info("✅ Roles assigned: {}", request.getRoles());
                } catch (Exception e) {
                    log.error("❌ Failed to assign roles: {}", e.getMessage());
                }
            }

            // ✅ MODIFIÉ — executeActionsEmail avec clientId + frontendUrl
            // L'employé définit son mdp sur Keycloak → redirigé vers ta plateforme Angular
            try {
                UserResource newUserResource = getUsersResource().get(userId);
                newUserResource.executeActionsEmail(
                        clientId,
                        frontendUrl,
                        Arrays.asList("UPDATE_PASSWORD", "VERIFY_EMAIL")
                );
                log.info("✅ Activation email sent to: {} (redirect → {})", request.getEmail(), frontendUrl);
            } catch (Exception e) {
                log.error("❌ Failed to send activation email: {}", e.getMessage());
            }

            try {
                syncUserToMySQL(userId);
                log.info("✅ User synced to MySQL: {}", userId);
            } catch (Exception e) {
                log.error("❌ Failed to sync to MySQL: {}", e.getMessage());
            }

            return userId;

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

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

        try {
            List<UserRepresentation> existingUsers = getUsersResource()
                    .search(request.getUsername(), true);
            if (!existingUsers.isEmpty()) {
                errors.add("Username already exists");
            }
        } catch (Exception e) {
            log.warn("Could not check username uniqueness: {}", e.getMessage());
        }

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

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    @Transactional
    public void updateUser(String userId, UserDTO userDTO) {
        log.info("🔄 Updating user in Keycloak: {}", userId);
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation kcUser = userResource.toRepresentation();
        kcUser.setEmail(userDTO.getEmail());
        kcUser.setFirstName(userDTO.getFirstName());
        kcUser.setLastName(userDTO.getLastName());
        kcUser.setEnabled(userDTO.isEnabled());
        userResource.update(kcUser);
        log.info("✅ User updated in Keycloak: {}", userId);
        syncUserToMySQL(userId);
    }

    @Transactional
    public void deleteUser(String userId) {
        log.info("🔄 Deleting user: {}", userId);
        if (userRepository.existsByKeycloakId(userId)) {
            userRepository.deleteById(userId);
            log.info("✅ User deleted from MySQL: {}", userId);
        }
        getUsersResource().get(userId).remove();
        log.info("✅ User deleted from Keycloak: {}", userId);
    }

    @Transactional
    public void toggleUserStatus(String userId, boolean enabled) {
        log.info("🔄 Toggling user status: {} -> {}", userId, enabled);
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
        syncUserToMySQL(userId);
    }

    // ✅ MODIFIÉ — sendPasswordResetEmail avec clientId + frontendUrl
    public void sendPasswordResetEmail(String userId) {
        getUsersResource().get(userId)
                .executeActionsEmail(
                        clientId,
                        frontendUrl,
                        Arrays.asList("UPDATE_PASSWORD")
                );
        log.info("✅ Password reset email resent for user: {} (redirect → {})", userId, frontendUrl);
    }

    // ========== ROLE MANAGEMENT ==========

    public List<RoleDTO> getAllRoles() {
        List<RoleRepresentation> roles = getRealmResource().roles().list();
        return roles.stream()
                .filter(role -> role.getName().startsWith("ROLE_"))
                .map(this::convertToRoleDTO)
                .collect(Collectors.toList());
    }

    public List<RoleDTO> getUserRoles(String userId) {
        List<RoleRepresentation> roles = getUsersResource()
                .get(userId).roles().realmLevel().listEffective();
        return roles.stream()
                .filter(role -> role.getName().startsWith("ROLE_"))
                .map(this::convertToRoleDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleNames) {
        log.info("🔄 Assigning roles to user {}: {}", userId, roleNames);
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
        }
        syncUserToMySQL(userId);
    }

    @Transactional
    public void removeRoles(String userId, List<String> roleNames) {
        log.info("🔄 Removing roles from user {}: {}", userId, roleNames);
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
        }
        syncUserToMySQL(userId);
    }

    public List<UserDTO> getUsersByRole(String roleName) {
        List<UserRepresentation> users = new ArrayList<>(
                getRealmResource().roles().get(roleName).getRoleUserMembers()
        );
        return users.stream().map(this::convertToUserDTO).collect(Collectors.toList());
    }

    public Optional<User> getMySQLUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    public List<User> getAllMySQLUsers() {
        return userRepository.findAll();
    }

    // ========== MYSQL SYNC ==========

    @Transactional
    public void syncUserToMySQL(String keycloakUserId) {
        try {
            UserRepresentation kcUser = getUsersResource().get(keycloakUserId).toRepresentation();
            List<RoleRepresentation> kcRoles = getUsersResource()
                    .get(keycloakUserId).roles().realmLevel().listEffective();

            Set<String> roles = kcRoles.stream()
                    .filter(role -> role.getName().startsWith("ROLE_"))
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());

            User dbUser = userRepository.findByKeycloakId(keycloakUserId).orElse(new User());
            dbUser.setKeycloakId(kcUser.getId());
            dbUser.setUsername(kcUser.getUsername());
            dbUser.setEmail(kcUser.getEmail());
            dbUser.setFirstName(kcUser.getFirstName());
            dbUser.setLastName(kcUser.getLastName());
            dbUser.setEnabled(kcUser.isEnabled());
            dbUser.setEmailVerified(kcUser.isEmailVerified() != null ? kcUser.isEmailVerified() : false);
            dbUser.setRoles(roles);

            if (kcUser.getCreatedTimestamp() != null && dbUser.getCreatedAt() == null) {
                dbUser.setCreatedAt(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(kcUser.getCreatedTimestamp()),
                                ZoneId.systemDefault()
                        )
                );
            }
            userRepository.save(dbUser);
            log.debug("✅ User synced to MySQL: {}", kcUser.getUsername());

        } catch (Exception e) {
            log.error("❌ Failed to sync user {} to MySQL: {}", keycloakUserId, e.getMessage());
            throw new RuntimeException("Failed to sync user to MySQL", e);
        }
    }

    @Transactional
    public void syncAllUsersToMySQL() {
        log.info("🔄 Starting full sync...");
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
        log.info("✅ Sync complete: {} success, {} errors", successCount, errorCount);
    }

    // ========== HELPERS ==========

    private UserDTO convertToUserDTO(UserRepresentation user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEnabled(user.isEnabled());
        dto.setEmailVerified(Boolean.TRUE.equals(user.isEmailVerified()));
        dto.setCreatedTimestamp(user.getCreatedTimestamp());

        try {
            List<String> roles = getUsersResource()
                    .get(user.getId()).roles().realmLevel().listEffective()
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