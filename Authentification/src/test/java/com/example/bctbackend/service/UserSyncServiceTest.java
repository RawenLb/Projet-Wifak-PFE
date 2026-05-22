package com.example.bctbackend.service;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ActiveProfiles("test")
@DisplayName("KeycloakAdminService — User Sync & Role Tests")
class UserSyncServiceTest {

    @Mock private Keycloak keycloak;
    @Mock private UserRepository userRepository;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;

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
    // toggleUserStatus
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toggleUserStatus — active l'utilisateur")
    void toggleUserStatus_active() {
        String userId = "user-1";
        UserResource userResource = mock(UserResource.class);
        UserRepresentation kcUser = buildKcUser(userId, "testuser", false);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(Collections.emptyList());
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.toggleUserStatus(userId, true);

        verify(userResource).update(argThat(u -> u.isEnabled()));
    }

    @Test
    @DisplayName("toggleUserStatus — désactive l'utilisateur")
    void toggleUserStatus_desactive() {
        String userId = "user-2";
        UserResource userResource = mock(UserResource.class);
        UserRepresentation kcUser = buildKcUser(userId, "testuser2", true);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(Collections.emptyList());
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.toggleUserStatus(userId, false);

        verify(userResource).update(argThat(u -> !u.isEnabled()));
    }

    // ══════════════════════════════════════════════════════════════
    // isValidEmail — testé via createUser
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("isValidEmail — email avec sous-domaine → valide")
    void isValidEmail_sousdomaine_valide() {
        // Testé indirectement via createUser — email valide ne lève pas d'exception sur le format
        // On vérifie que l'email "user@sub.domain.com" passe la validation de format
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("testvalid");
        req.setEmail("user@sub.domain.com");

        when(usersResource.search("testvalid", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("user@sub.domain.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        // Ne doit pas lever d'exception sur le format email
        // (va échouer sur la création Keycloak, mais pas sur la validation)
        assertThatThrownBy(() -> service.createUser(req))
            .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isValidEmail — email sans @ → IllegalArgumentException")
    void isValidEmail_sansArobase_invalide() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("testinvalid");
        req.setEmail("notanemail");

        when(usersResource.search("testinvalid", true)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }

    // ══════════════════════════════════════════════════════════════
    // getMySQLUser / getAllMySQLUsers
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMySQLUser — retourne l'utilisateur si présent")
    void getMySQLUser_present() {
        User user = new User();
        user.setKeycloakId("kc-1");
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));

        var result = service.getMySQLUser("kc-1");

        assertThat(result).isPresent();
        assertThat(result.get().getKeycloakId()).isEqualTo("kc-1");
    }

    @Test
    @DisplayName("getMySQLUser — retourne empty si absent")
    void getMySQLUser_absent() {
        when(userRepository.findByKeycloakId("kc-999")).thenReturn(Optional.empty());

        var result = service.getMySQLUser("kc-999");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllMySQLUsers — retourne tous les utilisateurs")
    void getAllMySQLUsers_retourneTous() {
        User u1 = new User(); u1.setKeycloakId("kc-1");
        User u2 = new User(); u2.setKeycloakId("kc-2");
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        var result = service.getAllMySQLUsers();

        assertThat(result).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private UserRepresentation buildKcUser(String id, String username, boolean enabled) {
        UserRepresentation u = new UserRepresentation();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setFirstName("First");
        u.setLastName("Last");
        u.setEnabled(enabled);
        u.setEmailVerified(false);
        u.setCreatedTimestamp(System.currentTimeMillis());
        return u;
    }
}
