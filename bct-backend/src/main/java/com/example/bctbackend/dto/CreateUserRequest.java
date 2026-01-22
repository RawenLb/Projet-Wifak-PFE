package com.example.bctbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class CreateUserRequest {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private boolean enabled = true;
    private List<String> roles = new ArrayList<>();

    public CreateUserRequest() {}

    // Getters with validation/trimming
    public String getUsername() {
        return username != null ? username.trim() : null;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email != null ? email.trim().toLowerCase() : null;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName != null ? firstName.trim() : "";
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName != null ? lastName.trim() : "";
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRoles() {
        return roles != null ? roles : new ArrayList<>();
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}