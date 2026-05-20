package com.example.bctbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class AuthentificationController {

    private static final Logger log = LoggerFactory.getLogger(AuthentificationController.class);

    private void logAuthInfo(Authentication authentication) {
        log.info("===== AUTH INFO =====");
        log.info("User : {}", authentication.getName());
        log.info("Authorities :");
        for (GrantedAuthority auth : authentication.getAuthorities()) {
            log.info(" - {}", auth.getAuthority());
        }
        log.info("=====================");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String admin(Authentication authentication) {
        logAuthInfo(authentication);
        return "ADMIN OK";
    }

    @GetMapping("/agent")
    @PreAuthorize("hasAuthority('ROLE_AGENT')")
    public String agent(Authentication authentication) {
        logAuthInfo(authentication);
        return "AGENT OK";
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public String manager(Authentication authentication) {
        logAuthInfo(authentication);
        return "MANAGER OK";
    }

    @GetMapping("/auditor")
    @PreAuthorize("hasAuthority('ROLE_AUDITOR')")
    public String auditor(Authentication authentication) {
        logAuthInfo(authentication);
        return "AUDITOR OK";
    }

    @GetMapping("/public/hello")
    public String publicHello() {
        log.info("PUBLIC endpoint accessed (no auth)");
        return "PUBLIC OK";
    }
}