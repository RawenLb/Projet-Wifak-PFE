package com.wifak.validationservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Workflow Declaration Service API")
                .description("""
                    API de gestion des déclarations réglementaires BCT — Banque Wifak.
                    
                    **Fonctionnalités :**
                    - Génération de déclarations XML, CSV, TXT
                    - Workflow de validation (Agent → Manager → BCT)
                    - Audit et traçabilité complète
                    - Analyse ML et aide intelligente
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Banque Wifak BCT")
                    .email("contact@wifakbank.com"))
                .license(new License()
                    .name("Propriétaire — Banque Wifak")))
            .servers(List.of(
                new Server().url("http://localhost:8084").description("Développement local"),
                new Server().url("http://localhost:8088").description("Via API Gateway")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Token JWT obtenu depuis Keycloak (realm: bct-realm)")));
    }
}
