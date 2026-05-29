package com.example.bctbackend;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BctBackendApplicationTests {

    // Mock Keycloak pour éviter la connexion réseau au démarrage
    @MockBean
    Keycloak keycloak;

    // Mock JwtDecoder pour éviter la connexion à Keycloak pour récupérer les clés JWK
    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}
