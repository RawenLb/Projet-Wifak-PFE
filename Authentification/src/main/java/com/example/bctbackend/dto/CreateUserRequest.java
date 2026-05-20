package com.example.bctbackend.dto;

import java.util.List;

/**
 * ✅ MODIFIÉ : Le champ password est supprimé.
 * L'admin crée le compte → Keycloak envoie un email à l'employé →
 * l'employé clique sur le lien et définit lui-même son mot de passe.
 */
public class CreateUserRequest {

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled = true;
    private List<String> roles;

    // ========== Getters & Setters ==========

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}