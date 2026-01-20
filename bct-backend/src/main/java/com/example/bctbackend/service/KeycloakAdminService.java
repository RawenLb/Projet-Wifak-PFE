package com.example.bctbackend.service;

import com.example.bctbackend.dto.CreateUserRequest;
import com.example.bctbackend.dto.RoleDTO;
import com.example.bctbackend.dto.UserDTO;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    private RealmResource getRealmResource() {
        return keycloak.realm(realm);
    }

    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    // ========== USER MANAGEMENT ==========

    /**
     * Get all users with their roles
     */
    public List<UserDTO> getAllUsers() {
        List<UserRepresentation> users = getUsersResource().list();

        return users.stream()
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
     * Create new user
     */
    public String createUser(CreateUserRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEnabled(request.isEnabled());
        user.setEmailVerified(true);

        // Create user
        Response response = getUsersResource().create(user);

        if (response.getStatus() != 201) {
            throw new RuntimeException("Failed to create user: " + response.getStatusInfo());
        }

        // Get created user ID from location header
        String locationHeader = response.getHeaderString("Location");
        String userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

        // Set password
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getPassword());
            credential.setTemporary(false);
            getUsersResource().get(userId).resetPassword(credential);
        }

        // Assign roles
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            assignRoles(userId, request.getRoles());
        }

        response.close();
        return userId;
    }

    /**
     * Update user
     */
    public void updateUser(String userId, UserDTO userDTO) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        user.setEmail(userDTO.getEmail());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEnabled(userDTO.isEnabled());

        userResource.update(user);
    }

    /**
     * Delete user
     */
    public void deleteUser(String userId) {
        getUsersResource().get(userId).remove();
    }

    /**
     * Enable/Disable user
     */
    public void toggleUserStatus(String userId, boolean enabled) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
    }

    /**
     * Reset user password (send email)
     */
    public void sendPasswordResetEmail(String userId) {
        getUsersResource().get(userId)
                .executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
    }

    // ========== ROLE MANAGEMENT ==========

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
     * Assign roles to user
     */
    public void assignRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAdd = roleNames.stream()
                .map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
                .collect(Collectors.toList());

        getUsersResource().get(userId).roles().realmLevel().add(rolesToAdd);
    }

    /**
     * Remove roles from user
     */
    public void removeRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToRemove = roleNames.stream()
                .map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
                .collect(Collectors.toList());

        getUsersResource().get(userId).roles().realmLevel().remove(rolesToRemove);
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
