package com.wifak.validationservice.controller;

import com.wifak.validationservice.dto.AiValidationResult;
import com.wifak.validationservice.dto.RejectRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.ValidationLog;
import com.wifak.validationservice.service.DeclarationService;
import com.wifak.validationservice.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "http://localhost:4200")
public class ValidationController {

    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);
    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    // 1. SOUMETTRE
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Declaration> submitForValidation(
            @PathVariable Long id,
            @RequestParam(required = false) String correctionComment) {
        return ResponseEntity.ok(validationService.submitForValidation(id, correctionComment));
    }

    // 2. VALIDER
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Declaration> validateDeclaration(@PathVariable Long id) {
        log.info("✅ [POST] /api/validation/{}/validate", id);
        return ResponseEntity.ok(validationService.validateDeclaration(id));
    }

    // 3. REJETER
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Declaration> rejectDeclaration(
            @PathVariable Long id,
            @RequestBody RejectRequest request) {
        log.info("❌ [POST] /api/validation/{}/reject", id);
        return ResponseEntity.ok(validationService.rejectDeclaration(id, request.getCommentaire()));
    }

    // 4. ENVOYER
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Declaration> markAsSent(@PathVariable Long id) {
        log.info("📨 [POST] /api/validation/{}/send", id);
        return ResponseEntity.ok(validationService.markAsSent(id));
    }

    // 5. PENDING
    @GetMapping("/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<Declaration>> getPendingDeclarations() {
        log.info("📋 [GET] /api/validation/pending");
        return ResponseEntity.ok(validationService.getPendingDeclarations());
    }

    // 6. STATS
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        log.info("📊 [GET] /api/validation/stats");
        return ResponseEntity.ok(validationService.getStats());
    }

    // 7. HISTORY
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<ValidationLog>> getHistory(@PathVariable Long id) {
        log.info("📜 [GET] /api/validation/{}/history", id);
        return ResponseEntity.ok(validationService.getHistory(id));
    }

    // 8. AI ANALYSIS
    @GetMapping("/{id}/ai-analysis")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AiValidationResult> aiAnalysis(@PathVariable Long id) {
        log.info("🤖 [GET] /api/validation/{}/ai-analysis", id);
        return ResponseEntity.ok(validationService.analyzeWithAi(id));
    }

    // 9. AI SUMMARY
    @GetMapping("/{id}/ai-summary")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> aiSummary(@PathVariable Long id) {
        log.info("📋 [GET] /api/validation/{}/ai-summary", id);
        return ResponseEntity.ok(validationService.getAiSummary(id));
    }

    // 10. COMPARAISON PÉRIODE PRÉCÉDENTE
    @GetMapping("/{id}/compare/{previousId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> compareWithPrevious(
            @PathVariable Long id,
            @PathVariable Long previousId) {
        log.info("📈 [GET] /api/validation/{}/compare/{}", id, previousId);
        return ResponseEntity.ok(validationService.compareWithPrevious(id, previousId));
    }

    // 11. REJECT TEMPLATES — templates de motifs de rejet prédéfinis
    @GetMapping("/reject-templates")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<Map<String, String>>> getRejectTemplates() {
        log.info("📝 [GET] /api/validation/reject-templates");
        List<Map<String, String>> templates = List.of(
            Map.of("id", "1", "label", "Données incomplètes",
                   "text", "La déclaration est incomplète. Veuillez vérifier et compléter tous les champs obligatoires."),
            Map.of("id", "2", "label", "Montants incorrects",
                   "text", "Les montants déclarés ne correspondent pas aux données comptables. Veuillez corriger les montants."),
            Map.of("id", "3", "label", "Période incorrecte",
                   "text", "La période de déclaration est incorrecte. Veuillez vérifier les dates de début et de fin."),
            Map.of("id", "4", "label", "Format non conforme",
                   "text", "Le format du fichier ne respecte pas les spécifications BCT. Veuillez régénérer la déclaration."),
            Map.of("id", "5", "label", "Données fictives détectées",
                   "text", "Des données de test ou fictives ont été détectées. Veuillez utiliser des données réelles.")
        );
        return ResponseEntity.ok(templates);
    }
}
