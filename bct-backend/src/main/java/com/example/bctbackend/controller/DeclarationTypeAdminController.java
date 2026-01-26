package com.example.bctbackend.controller;

import com.example.bctbackend.entities.DeclarationType;
import com.example.bctbackend.service.DeclarationTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/declaration-types")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class DeclarationTypeAdminController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationTypeAdminController.class);
    private final DeclarationTypeService service;

    public DeclarationTypeAdminController(DeclarationTypeService service) {
        this.service = service;
    }

    // ➕ Ajouter
    @PostMapping
    public ResponseEntity<DeclarationType> create(@RequestBody DeclarationType declarationType) {
        log.info("➕ Creating declaration type {}", declarationType.getCode());
        return ResponseEntity.ok(service.create(declarationType));
    }

    // 📋 Lister
    @GetMapping
    public ResponseEntity<List<DeclarationType>> getAll() {
        log.info("📋 Fetching all declaration types");
        return ResponseEntity.ok(service.getAll());
    }

    // ✏️ Modifier
    @PutMapping("/{id}")
    public ResponseEntity<DeclarationType> update(
            @PathVariable Long id,
            @RequestBody DeclarationType declarationType) {

        log.info("✏️ Updating declaration type {}", id);
        return ResponseEntity.ok(service.update(id, declarationType));
    }

    // 🗑️ Supprimer
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("🗑️ Deleting declaration type {}", id);
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Declaration type deleted successfully"));
    }

    // 🔄 Activer / Désactiver
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeclarationType> toggleStatus(@PathVariable Long id) {
        log.info("🔄 Toggling declaration type {}", id);
        return ResponseEntity.ok(service.toggleStatus(id));
    }
}
