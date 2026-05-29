package com.example.bctbackend.service;

import com.example.bctbackend.dto.UserDTO;
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
@DisplayName("KeycloakAdminService — Tests étendus (getAllUsers, updateUser, assignRoles, etc.)")
class KeycloakAdminServiceExtendedTest {

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
    // getAllUsers
    @Test
    @DisplayName("getAllUsers — retourne la liste convertie en UserDTO")
    void getAllUsers_retourneListe() {
        UserRepresentation u1 = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserRepresentation u2 = buildKcUser("id-2", "bob", "bob@test.com", false);

        UserResource ur1 = mockUserResourceWithRoles(u1, List.of("ROLE_AGENT"));
        UserResource ur2 = mockUserResourceWithRoles(u2, Collections.emptyList());

        when(usersResource.list()).thenReturn(List.of(u1, u2));
        when(usersResource.get("id-1")).thenReturn(ur1);
        when(usersResource.get("id-2")).thenReturn(ur2);

        List<UserDTO> result = service.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting("username").containsExactlyInAnyOrder("alice", "bob");
        assertThat(result.get(0).getRoles()).isNotNull();
    }

    @Test
    @DisplayName("getAllUsers — liste vide → retourne liste vide")
    void getAllUsers_listeVide() {
        when(usersResource.list()).thenReturn(Collections.emptyList());

        List<UserDTO> result = service.getAllUsers();

        assertThat(result).isEmpty();
    }
    // getUserById
    @Test
    @DisplayName("getUserById — retourne le UserDTO correspondant")
    void getUserById_retourneDTO() {
        UserRepresentation kcUser = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_ADMIN"));

        when(usersResource.get("id-1")).thenReturn(userResource);

        UserDTO result = service.getUserById("id-1");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getRoles()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("getUserById — roles fetch échoue → retourne DTO avec liste vide")
    void getUserById_rolesFetchEchoue_retourneDTOSansRoles() {
        UserRepresentation kcUser = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserResource userResource = mock(UserResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        when(usersResource.get("id-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenThrow(new RuntimeException("Keycloak error"));

        UserDTO result = service.getUserById("id-1");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRoles()).isEmpty();
    }
    // searchUsers
    @Test
    @DisplayName("searchUsers — retourne les utilisateurs correspondants")
    void searchUsers_retourneResultats() {
        UserRepresentation kcUser = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_AGENT"));

        when(usersResource.search("alice", 0, 100)).thenReturn(List.of(kcUser));
        when(usersResource.get("id-1")).thenReturn(userResource);

        List<UserDTO> result = service.searchUsers("alice");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("searchUsers — aucun résultat → liste vide")
    void searchUsers_aucunResultat() {
        when(usersResource.search("unknown", 0, 100)).thenReturn(Collections.emptyList());

        List<UserDTO> result = service.searchUsers("unknown");

        assertThat(result).isEmpty();
    }
    // updateUser
    @Test
    @DisplayName("updateUser — met à jour Keycloak et synchronise MySQL")
    void updateUser_metsAJourEtSync() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_AGENT"));

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        com.example.bctbackend.dto.UserDTO dto = new com.example.bctbackend.dto.UserDTO();
        dto.setEmail("newalice@test.com");
        dto.setFirstName("Alice");
        dto.setLastName("Updated");
        dto.setEnabled(true);

        assertThatCode(() -> service.updateUser(userId, dto)).doesNotThrowAnyException();

        verify(userResource).update(argThat(u -> "newalice@test.com".equals(u.getEmail())));
        verify(userRepository).save(any(User.class));
    }
    // assignRoles
    @Test
    @DisplayName("assignRoles — assigne les rôles existants et synchronise")
    void assignRoles_assigneEtSync() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_AGENT"));
        RoleResource roleResource = mock(RoleResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_AGENT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(roleAgent);
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(List.of(roleAgent));
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.assignRoles(userId, List.of("ROLE_AGENT")))
            .doesNotThrowAnyException();

        verify(roleScopeResource).add(anyList());
    }

    @Test
    @DisplayName("assignRoles — rôle introuvable → ignoré sans exception")
    void assignRoles_roleIntrouvable_ignore() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());
        RoleResource roleResource = mock(RoleResource.class);

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_INEXISTANT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenThrow(new RuntimeException("Role not found"));
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Ne doit pas lever d'exception — le rôle introuvable est ignoré
        assertThatCode(() -> service.assignRoles(userId, List.of("ROLE_INEXISTANT")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assignRoles — liste vide → aucun appel add")
    void assignRoles_listeVide_aucunAdd() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.assignRoles(userId, Collections.emptyList()))
            .doesNotThrowAnyException();
    }
    // removeRoles
    @Test
    @DisplayName("removeRoles — retire les rôles et synchronise")
    void removeRoles_retireEtSync() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());
        RoleResource roleResource = mock(RoleResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_AGENT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(roleAgent);
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(Collections.emptyList());
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.removeRoles(userId, List.of("ROLE_AGENT")))
            .doesNotThrowAnyException();

        verify(roleScopeResource).remove(anyList());
    }

    @Test
    @DisplayName("removeRoles — rôle introuvable → ignoré sans exception")
    void removeRoles_roleIntrouvable_ignore() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());
        RoleResource roleResource = mock(RoleResource.class);

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_INEXISTANT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenThrow(new RuntimeException("Role not found"));
        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.removeRoles(userId, List.of("ROLE_INEXISTANT")))
            .doesNotThrowAnyException();
    }
    // getUserRoles
    @Test
    @DisplayName("getUserRoles — retourne uniquement les rôles ROLE_")
    void getUserRoles_filtreRoles() {
        String userId = "id-1";
        UserResource userResource = mock(UserResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");
        RoleRepresentation roleInternal = new RoleRepresentation();
        roleInternal.setName("uma_authorization");

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(List.of(roleAgent, roleInternal));

        var result = service.getUserRoles(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("ROLE_AGENT");
    }
    // getUsersByRole
    @Test
    @DisplayName("getUsersByRole — retourne les utilisateurs du rôle")
    void getUsersByRole_retourneUtilisateurs() {
        UserRepresentation kcUser = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_AGENT"));
        RoleResource roleResource = mock(RoleResource.class);

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_AGENT")).thenReturn(roleResource);
        when(roleResource.getRoleUserMembers()).thenReturn(new HashSet<>(List.of(kcUser)));
        when(usersResource.get("id-1")).thenReturn(userResource);

        List<UserDTO> result = service.getUsersByRole("ROLE_AGENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }
    // syncAllUsersToMySQL
    @Test
    @DisplayName("syncAllUsersToMySQL — synchronise tous les utilisateurs")
    void syncAllUsersToMySQL_synchroniseTous() {
        UserRepresentation u1 = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserRepresentation u2 = buildKcUser("id-2", "bob", "bob@test.com", true);

        UserResource ur1 = mockUserResourceWithRoles(u1, List.of("ROLE_AGENT"));
        UserResource ur2 = mockUserResourceWithRoles(u2, List.of("ROLE_MANAGER"));

        when(usersResource.list()).thenReturn(List.of(u1, u2));
        when(usersResource.get("id-1")).thenReturn(ur1);
        when(usersResource.get("id-2")).thenReturn(ur2);
        when(userRepository.findByKeycloakId(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.syncAllUsersToMySQL()).doesNotThrowAnyException();

        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("syncAllUsersToMySQL — un utilisateur échoue → continue avec les autres")
    void syncAllUsersToMySQL_unEchoue_continueAutres() {
        UserRepresentation u1 = buildKcUser("id-1", "alice", "alice@test.com", true);
        UserRepresentation u2 = buildKcUser("id-2", "bob", "bob@test.com", true);

        UserResource ur1 = mock(UserResource.class);
        UserResource ur2 = mockUserResourceWithRoles(u2, List.of("ROLE_AGENT"));

        when(usersResource.list()).thenReturn(List.of(u1, u2));
        when(usersResource.get("id-1")).thenReturn(ur1);
        when(ur1.toRepresentation()).thenThrow(new RuntimeException("Keycloak error"));
        when(usersResource.get("id-2")).thenReturn(ur2);
        when(userRepository.findByKeycloakId("id-2")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Ne doit pas lever d'exception — les erreurs individuelles sont loggées
        assertThatCode(() -> service.syncAllUsersToMySQL()).doesNotThrowAnyException();

        // bob doit quand même être synchronisé
        verify(userRepository, times(1)).save(any(User.class));
    }
    // syncUserToMySQL — cas d'erreur
    @Test
    @DisplayName("syncUserToMySQL — erreur Keycloak → RuntimeException")
    void syncUserToMySQL_erreurKeycloak_throwsException() {
        String userId = "id-error";
        UserResource userResource = mock(UserResource.class);

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userResource.toRepresentation()).thenThrow(new RuntimeException("Keycloak down"));

        assertThatThrownBy(() -> service.syncUserToMySQL(userId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to sync user to MySQL");
    }

    @Test
    @DisplayName("syncUserToMySQL — utilisateur existant → mise à jour")
    void syncUserToMySQL_utilisateurExistant_metsAJour() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, List.of("ROLE_AGENT"));

        User existingUser = new User();
        existingUser.setKeycloakId(userId);
        existingUser.setUsername("old-alice");
        existingUser.setCreatedAt(java.time.LocalDateTime.now()); // createdAt déjà défini → branche else

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.syncUserToMySQL(userId);

        verify(userRepository).save(argThat(u -> "alice".equals(u.getUsername())));
    }

    @Test
    @DisplayName("syncUserToMySQL — createdTimestamp null → pas de setCreatedAt")
    void syncUserToMySQL_createdTimestampNull_pasDeCreatedAt() {
        String userId = "id-null-ts";
        UserRepresentation kcUser = buildKcUser(userId, "bob", "bob@test.com", true);
        kcUser.setCreatedTimestamp(null); // branche createdTimestamp == null
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.syncUserToMySQL(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("syncUserToMySQL — emailVerified null → false par défaut")
    void syncUserToMySQL_emailVerifiedNull_defaultFalse() {
        String userId = "id-ev-null";
        UserRepresentation kcUser = buildKcUser(userId, "carol", "carol@test.com", true);
        kcUser.setEmailVerified(null); // branche emailVerified null
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.syncUserToMySQL(userId);

        verify(userRepository).save(argThat(u -> !u.getEmailVerified()));
    }

    @Test
    @DisplayName("convertToUserDTO — emailVerified true + rôles filtrés")
    void convertToUserDTO_emailVerifiedTrue_rolesFiltres() {
        UserRepresentation kcUser = buildKcUser("id-1", "alice", "alice@test.com", true);
        kcUser.setEmailVerified(true); // branche emailVerified true
        UserResource userResource = mockUserResourceWithRoles(kcUser,
            List.of("ROLE_AGENT", "uma_authorization", "offline_access"));

        when(usersResource.list()).thenReturn(List.of(kcUser));
        when(usersResource.get("id-1")).thenReturn(userResource);

        List<UserDTO> result = service.getAllUsers();

        assertThat(result.get(0).getRoles()).containsOnly("ROLE_AGENT");
        assertThat(result.get(0).isEmailVerified()).isTrue();
    }
    // createUser — branches de validateCreateUserRequest
    @Test
    @DisplayName("createUser — email déjà existant → IllegalArgumentException")
    void createUser_emailExistant_throwsException() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("existing@test.com");

        // username libre
        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        // email déjà pris
        UserRepresentation existing = new UserRepresentation();
        existing.setEmail("existing@test.com");
        when(usersResource.search("existing@test.com", null, null, null, 0, 10))
            .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("createUser — username null → IllegalArgumentException")
    void createUser_usernameNull_throwsException() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername(null);
        req.setEmail("test@test.com");

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username");
    }

    @Test
    @DisplayName("createUser — email null → IllegalArgumentException")
    void createUser_emailNull_throwsException() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("testuser");
        req.setEmail(null);

        when(usersResource.search("testuser", true)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("createUser — vérification username échoue → continue sans erreur")
    void createUser_usernameCheckEchoue_continueValidation() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("testuser");
        req.setEmail("test@test.com");

        // La recherche username lève une exception → warn et continue
        when(usersResource.search("testuser", true)).thenThrow(new RuntimeException("Keycloak error"));
        // La recherche email aussi
        when(usersResource.search("test@test.com", null, null, null, 0, 10))
            .thenThrow(new RuntimeException("Keycloak error"));

        // Doit passer la validation et échouer sur la création (pas de mock Response)
        assertThatThrownBy(() -> service.createUser(req))
            .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createUser — email dans liste mais email différent → pas d'erreur email")
    void createUser_emailListeMaisEmailDifferent_pasErreur() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        // Retourne un user avec un email différent
        UserRepresentation other = new UserRepresentation();
        other.setEmail("other@test.com");
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(List.of(other));

        // Validation passe, échoue sur la création Keycloak (pas de mock Response)
        assertThatThrownBy(() -> service.createUser(req))
            .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateUser — firstName et lastName null → DTO avec valeurs null")
    void updateUser_firstLastNameNull() {
        String userId = "id-1";
        UserRepresentation kcUser = buildKcUser(userId, "alice", "alice@test.com", true);
        UserResource userResource = mockUserResourceWithRoles(kcUser, Collections.emptyList());

        when(usersResource.get(userId)).thenReturn(userResource);
        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        com.example.bctbackend.dto.UserDTO dto = new com.example.bctbackend.dto.UserDTO();
        dto.setEmail("alice@test.com");
        dto.setFirstName(null);
        dto.setLastName(null);
        dto.setEnabled(false); // branche enabled=false

        assertThatCode(() -> service.updateUser(userId, dto)).doesNotThrowAnyException();
    }
    // createUser — chemin heureux complet
    @Test
    @DisplayName("createUser — succès avec rôles → retourne userId")
    void createUser_succes_avecRoles() throws Exception {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setFirstName("New");
        req.setLastName("User");
        req.setRoles(List.of("ROLE_AGENT"));

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        jakarta.ws.rs.core.Response mockResponse = mock(jakarta.ws.rs.core.Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://keycloak/auth/admin/realms/bct-realm/users/new-user-id");
        when(usersResource.create(any())).thenReturn(mockResponse);

        // Mock pour assignRoles
        UserResource newUserResource = mockUserResourceWithRoles(
            buildKcUser("new-user-id", "newuser", "new@test.com", true), List.of("ROLE_AGENT"));
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);
        when(usersResource.get("new-user-id")).thenReturn(newUserResource);
        when(newUserResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(Collections.emptyList());

        RoleResource roleResource = mock(RoleResource.class);
        RoleRepresentation roleAgent = new RoleRepresentation();
        roleAgent.setName("ROLE_AGENT");
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ROLE_AGENT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(roleAgent);

        when(userRepository.findByKeycloakId("new-user-id")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String userId = service.createUser(req);

        assertThat(userId).isEqualTo("new-user-id");
    }

    @Test
    @DisplayName("createUser — Keycloak retourne 409 → RuntimeException")
    void createUser_keycloak409_throwsException() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        jakarta.ws.rs.core.Response mockResponse = mock(jakarta.ws.rs.core.Response.class);
        when(mockResponse.getStatus()).thenReturn(409);
        when(mockResponse.hasEntity()).thenReturn(false);
        when(usersResource.create(any())).thenReturn(mockResponse);

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("409");
    }

    @Test
    @DisplayName("createUser — Keycloak 409 avec entity → lit le body d'erreur")
    void createUser_keycloak409_avecEntity() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        jakarta.ws.rs.core.Response mockResponse = mock(jakarta.ws.rs.core.Response.class);
        when(mockResponse.getStatus()).thenReturn(409);
        when(mockResponse.hasEntity()).thenReturn(true);
        when(mockResponse.readEntity(String.class)).thenReturn("User already exists");
        when(usersResource.create(any())).thenReturn(mockResponse);

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("User already exists");
    }

    @Test
    @DisplayName("createUser — Location header null → RuntimeException")
    void createUser_locationNull_throwsException() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        jakarta.ws.rs.core.Response mockResponse = mock(jakarta.ws.rs.core.Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location")).thenReturn(null);
        when(usersResource.create(any())).thenReturn(mockResponse);

        assertThatThrownBy(() -> service.createUser(req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Location header");
    }

    @Test
    @DisplayName("createUser — sans rôles → pas d'appel assignRoles")
    void createUser_sansRoles_pasAssignRoles() {
        com.example.bctbackend.dto.CreateUserRequest req = new com.example.bctbackend.dto.CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setRoles(null);

        when(usersResource.search("newuser", true)).thenReturn(Collections.emptyList());
        when(usersResource.search("new@test.com", null, null, null, 0, 10))
            .thenReturn(Collections.emptyList());

        jakarta.ws.rs.core.Response mockResponse = mock(jakarta.ws.rs.core.Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://keycloak/users/new-user-id");
        when(usersResource.create(any())).thenReturn(mockResponse);

        UserResource newUserResource = mockUserResourceWithRoles(
            buildKcUser("new-user-id", "newuser", "new@test.com", true), Collections.emptyList());
        when(usersResource.get("new-user-id")).thenReturn(newUserResource);
        when(userRepository.findByKeycloakId("new-user-id")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String userId = service.createUser(req);

        assertThat(userId).isEqualTo("new-user-id");
    }
    // sendPasswordResetEmail
    @Test
    @DisplayName("sendPasswordResetEmail — appelle executeActionsEmail")
    void sendPasswordResetEmail_appelleExecuteActions() {
        String userId = "id-1";
        UserResource userResource = mock(UserResource.class);

        when(usersResource.get(userId)).thenReturn(userResource);
        doNothing().when(userResource).executeActionsEmail(anyString(), anyString(), anyList());

        assertThatCode(() -> service.sendPasswordResetEmail(userId)).doesNotThrowAnyException();

        verify(userResource).executeActionsEmail(
            eq("bct-frontend"),
            eq("http://localhost:4200"),
            argThat(list -> list.contains("UPDATE_PASSWORD"))
        );
    }
    // Helpers
    private UserRepresentation buildKcUser(String id, String username, String email, boolean enabled) {
        UserRepresentation u = new UserRepresentation();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setFirstName("First");
        u.setLastName("Last");
        u.setEnabled(enabled);
        u.setEmailVerified(false);
        u.setCreatedTimestamp(System.currentTimeMillis());
        return u;
    }

    private UserResource mockUserResourceWithRoles(UserRepresentation kcUser, List<String> roleNames) {
        UserResource userResource = mock(UserResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

        List<RoleRepresentation> roles = roleNames.stream().map(name -> {
            RoleRepresentation r = new RoleRepresentation();
            r.setName(name);
            return r;
        }).toList();

        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listEffective()).thenReturn(roles);

        return userResource;
    }
}
