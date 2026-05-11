package com.wifak.chatservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            ((List<String>) realmAccess.get("roles")).stream()
                .map(role -> new SimpleGrantedAuthority(
                    role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase()))
                .forEach(authorities::add);
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(clientAccess -> {
                if (clientAccess instanceof Map<?, ?> map) {
                    Object rolesObj = map.get("roles");
                    if (rolesObj instanceof List<?> roleList) {
                        roleList.stream()
                            .filter(r -> r instanceof String)
                            .map(r -> new SimpleGrantedAuthority(
                                ((String) r).startsWith("ROLE_") ? (String) r : "ROLE_" + ((String) r).toUpperCase()))
                            .forEach(authorities::add);
                    }
                }
            });
        }
        return authorities;
    }
}
