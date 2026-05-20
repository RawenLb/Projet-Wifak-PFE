package com.example.bctbackend.service;

import com.example.bctbackend.dto.CreateUserRequest;
import com.example.bctbackend.entities.User;
import com.example.bctbackend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.ws.rs.core.Response;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("KeycloakAdminService — Tests unitaires")
class KeycloakAdminServiceTest {

    @Mock private Keycloak keycloak;
    @Mock private UserRepository userRepository;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private RolesResource rolesResource;

    @InjectMocks
    private KeycloakAdminService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "realm", "bct-realm");
        ReflectionTestUtils.setField(service, "clientId", "bct-frontend");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");

        when(keycloak.realm("bct-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    // ══════════════════════════════════════════════════════════════
    // isValidEmail — méthode privée testée via createUser
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createUser — email invalide → IllegalArgumentException")
    void createUser_emailInvalide_throwsException() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("testuser");
        req.setEmail("email-invalide");

        // Mock pour éviter NPE sur la recherche username
        when(usersResource.search("testuser", true)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }

    @Test
    @DisplayName("createUser — username vide → IllegalArgumentException")
    void createUser_usernameVide_throwsException() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("");
        req.setEmail("test@test.com");

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username");
    }

    @Test
    @DisplayName("createUser — email vide → IllegalArgumentException")
    void createUser_emailVide_throwsException() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("testuser");
        req.setEmail("");

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("createUser — username déjà existant → IllegalArgumentException")
    void createUser_usernameExistant_throwsException() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("existinguser");
        req.setEmail("new@test.com");

        UserRepresentation existing = new UserRepresentation();
        existing.setUsername("existinguser");
        when(usersResource.search("existinguser", true)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    // ══════════════════════════════════════════════════════════════
    // deleteUser
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteUser — supprime d'abord MySQL puis Keycloak")
    void deleteUser_ordreCorrect() {
        String userId = "user-123";
        UserResource userResource = mock(UserResource.class);

        when(userRepository.existsByKeycloakId(userId)).thenReturn(true);
        doNothing().when(userRepository).deleteById(userId);
        when(usersResource.get(userId)).thenReturn(userResource);
        doNothing().when(userResource).remove();

        service.deleteUser(userId);

        // Vérifier l'ordre : MySQL avant Keycloak
        var inOrder = inOrder(userRepository, userResource);
        inOrder.verify(userRepository).deleteById(userId);
        inOrder.verify(userResource).remove();
    }

    @Test
    @DisplayName("deleteUser — utilisateur absent de MySQL → supprime uniquement Keycloak")
    void deleteUser_absenceMySQL_supprimerKeycloakSeulement() {
        String userId = "user-456";
        UserResource userResource = mock(UserResource.class);

        when(userRepository.existsByKeycloakId(userId)).thenReturn(false);
        when(usersResource.get(userId)).thenReturn(userResource);
        doNothing().when(userResource).remove();

        service.deleteUser(userId);

        verify(userRepository, never()).deleteById(any());
        verify(userResource).remove();
    }

    // ══════════════════════════════════════════════════════════════
    // getAllRoles — filtre ROLE_
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllRoles — filtre uniquement les rôles ROLE_")
    void getAllRoles_filtreRolesCorrectement() {
        when(realmResource.roles()).thenReturn(rolesResource);

        RoleRepresentation roleAdmin = new RoleRepresentation();
        roleAdmin.setName("ROLE_ADMIN");
        RoleRepresentation roleUser = new RoleRepresentation();
        roleUser.setName("uma_authorization");
        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");

        when(rolesResource.list()).thenReturn(List.of(roleAdmin, roleUser, roleAgent));

        var roles = service.getAllRoles();

        assertThat(roles).hasSize(2);
        assertThat(roles).extracting("name")
            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_AGENT");
    }

    // ══════════════════════════════════════════════════════════════
    // syncUserToMySQL
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("syncUserToMySQL — crée un nouvel utilisateur si absent")
    void syncUserToMySQL_nouvelUtilisateur() {
        String userId = "new-user-id";
        UserResource userResource = mock(UserResource.class);
        RolesResource userRolesResource = mock(RolesResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setId(userId);
        kcUser.setUsername("newuser");
        kcUser.setEmail("new@test.com");
        kcUser.setFirstName("New");
        kcUser.setLastName("User");
        kcUser.setEnabled(true);
        kcUser.setEmailVerified(false);
        kcUser.setCreatedTimestamp(System.currentTimeMillis());

        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(List.of(roleAgent));
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.syncUserToMySQL(userId);

        verify(userRepository).save(argThat(user ->
            "newuser".equals(user.getUsername()) &&
            "new@test.com".equals(user.getEmail()) &&
            user.getRoles().contains("ROLE_AGENT")
        ));
    }
}
