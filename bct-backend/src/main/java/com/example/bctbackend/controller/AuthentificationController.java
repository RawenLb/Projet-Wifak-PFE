package com.example.bctbackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class AuthentificationController {

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String admin() {
        return "ADMIN OK";
    }

    @GetMapping("/agent")
    @PreAuthorize("hasAuthority('ROLE_AGENT')")
    public String agent() {
        return "AGENT OK";
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAuthority('ROLE_MANAGER')")
    public String manager() {
        return "MANAGER OK";
    }

    @GetMapping("/auditor")
    @PreAuthorize("hasAuthority('ROLE_AUDITOR')")
    public String auditor() {
        return "AUDITOR OK";
    }

    @GetMapping("/public/hello")
    public String publicHello() {
        return "PUBLIC OK";
    }
}
