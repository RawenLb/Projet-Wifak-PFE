package com.example.bctbackend.dto;

import com.example.bctbackend.entities.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Authentification DTOs et Entités — Tests getters/setters")
class DtoGetterSetterTest {

    @Test
    void userDTO_gettersSetters() {
        UserDTO dto = new UserDTO();
        dto.setId("user-1");
        dto.setUsername("alice");
        dto.setEmail("alice@test.com");
        dto.setFirstName("Alice");
        dto.setLastName("Dupont");
        dto.setEnabled(true);
        dto.setEmailVerified(true);
        dto.setCreatedTimestamp(1000L);
        dto.setRoles(List.of("ROLE_AGENT"));

        assertThat(dto.getId()).isEqualTo("user-1");
        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getEmail()).isEqualTo("alice@test.com");
        assertThat(dto.getFirstName()).isEqualTo("Alice");
        assertThat(dto.getLastName()).isEqualTo("Dupont");
        assertThat(dto.isEnabled()).isTrue();
        assertThat(dto.isEmailVerified()).isTrue();
        assertThat(dto.getCreatedTimestamp()).isEqualTo(1000L);
        assertThat(dto.getRoles()).containsExactly("ROLE_AGENT");
    }

    @Test
    void roleDTO_gettersSetters() {
        RoleDTO dto = new RoleDTO();
        dto.setId("role-1");
        dto.setName("ROLE_ADMIN");
        dto.setDescription("Administrateur");

        assertThat(dto.getId()).isEqualTo("role-1");
        assertThat(dto.getName()).isEqualTo("ROLE_ADMIN");
        assertThat(dto.getDescription()).isEqualTo("Administrateur");
    }

    @Test
    void createUserRequest_gettersSetters() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setFirstName("New");
        req.setLastName("User");
        req.setEnabled(true);
        req.setRoles(List.of("ROLE_AGENT"));

        assertThat(req.getUsername()).isEqualTo("newuser");
        assertThat(req.getEmail()).isEqualTo("new@test.com");
        assertThat(req.getFirstName()).isEqualTo("New");
        assertThat(req.getLastName()).isEqualTo("User");
        assertThat(req.isEnabled()).isTrue();
        assertThat(req.getRoles()).containsExactly("ROLE_AGENT");
    }

    @Test
    void user_entity_gettersSetters() {
        User user = new User();
        user.setKeycloakId("kc-1");
        user.setUsername("alice");
        user.setEmail("alice@test.com");
        user.setFirstName("Alice");
        user.setLastName("Dupont");
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Set.of("ROLE_AGENT"));

        assertThat(user.getKeycloakId()).isEqualTo("kc-1");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@test.com");
        assertThat(user.getFirstName()).isEqualTo("Alice");
        assertThat(user.getLastName()).isEqualTo("Dupont");
        assertThat(user.getEnabled()).isTrue();
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getRoles()).containsExactly("ROLE_AGENT");
    }
}
