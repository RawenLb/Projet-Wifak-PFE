package com.wifak.jiraintegrationservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 1. Rôles realm_access (Keycloak)
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            roles.stream()
                    .map(role -> new SimpleGrantedAuthority(
                            role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase()
                    ))
                    .forEach(authorities::add);
        }

        // 2. Rôles resource_access (client-specific)
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(clientAccess -> {
                if (clientAccess instanceof Map) {
                    Object rolesObj = ((Map<?, ?>) clientAccess).get("roles");
                    if (rolesObj instanceof List) {
                        ((List<String>) rolesObj).stream()
                                .map(r -> new SimpleGrantedAuthority(
                                        r.startsWith("ROLE_") ? r : "ROLE_" + r.toUpperCase()
                                ))
                                .forEach(authorities::add);
                    }
                }
            });
        }

        return authorities;
    }
}