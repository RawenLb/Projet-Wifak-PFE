package com.wifak.validationservice.controller;

import com.wifak.validationservice.dto.GenerateDeclarationRequest;
import com.wifak.validationservice.dto.XsdSqlMappingRequest;
import com.wifak.validationservice.entities.Declaration;
import com.wifak.validationservice.entities.DeclarationType;
import com.wifak.validationservice.repositories.DeclarationTypeRepository;
import com.wifak.validationservice.service.DeclarationService;
import com.wifak.validationservice.service.XsdAnalyzerService;
import com.wifak.validationservice.service.XmlGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/declarations")
@CrossOrigin(origins = "http://localhost:4200")
public class DeclarationController {

    private static final Logger log = LoggerFactory.getLogger(DeclarationController.class);

    private final DeclarationService        declarationService;
    private final XsdAnalyzerService        xsdAnalyzerService;
    private final XmlGenerationService      xmlGenerationService;
    private final DeclarationTypeRepository typeRepository;

    public DeclarationController(DeclarationService declarationService,
                                 XsdAnalyzerService xsdAnalyzerService,
                                 XmlGenerationService xmlGenerationService,
                                 DeclarationTypeRepository typeRepository) {
        this.declarationService   = declarationService;
        this.xsdAnalyzerService   = xsdAnalyzerService;
        this.xmlGenerationService = xmlGenerationService;
        this.typeRepository       = typeRepository;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
    // GENERATE — sans mapping (comportement existant)
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> generateDeclaration(
            @RequestBody GenerateDeclarationRequest req) {

        log.info("🚀 Génération — Type: {}, Période: {}", req.getDeclarationTypeId(), req.getPeriode());

        Declaration saved = declarationService.generateAndSave(
                req.getDeclarationTypeId(),
                req.getPeriode(),
                req.getDateDebut(),
                req.getDateFin()
        );
        declarationService.notifyJiraTicketCreation(saved.getId(), getCurrentUsername());
        return ResponseEntity.ok(saved);
    }
    // ✅ ANALYZE MAPPING — analyse compatibilité XSD ↔ SQL
    // POST /api/declarations/analyze-mapping
    // Body: { declarationTypeId, dateDebut, dateFin }
    @PostMapping("/analyze-mapping")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<?> analyzeMapping(@RequestBody AnalyzeMappingRequest req) {
        log.info("🔍 Analyse mapping — Type: {}", req.getDeclarationTypeId());

        try {
            DeclarationType type = typeRepository.findById(req.getDeclarationTypeId())
                    .orElseThrow(() -> new RuntimeException("Type introuvable: " + req.getDeclarationTypeId()));

            // Vérifier que c'est du XML
            if (type.getFormat() != DeclarationType.DeclarationFormat.XML) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("applicable", false);
                resp.put("message", "Le mapping XSD ↔ SQL s'applique uniquement aux déclarations de format XML.");
                return ResponseEntity.ok(resp);
            }

            // Vérifier que le XSD est disponible
            if (type.getXsdContent() == null || type.getXsdContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Aucun XSD n'est configuré pour ce type. Uploadez d'abord le fichier XSD."
                ));
            }

            // Vérifier que la SQL est disponible
            if (type.getSqlQuery() == null || type.getSqlQuery().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Aucune requête SQL configurée pour ce type."
                ));
            }

            // Extraire les colonnes SQL
            LocalDate dateDebut = req.getDateDebut() != null ? req.getDateDebut() : LocalDate.now().withDayOfMonth(1);
            LocalDate dateFin   = req.getDateFin()   != null ? req.getDateFin()   : LocalDate.now();

            List<String> sqlColumns;
            try {
                sqlColumns = xmlGenerationService.extractColumnsFromSql(type.getSqlQuery(), dateDebut, dateFin);
            } catch (Exception e) {
                log.warn("⚠️ Impossible d'extraire les colonnes SQL: {}", e.getMessage());
                sqlColumns = List.of(); // On continue avec une liste vide
            }

            // Analyser la compatibilité XSD ↔ SQL
            XsdAnalyzerService.MappingAnalysisResult analysis =
                    xsdAnalyzerService.analyzeCompatibility(type.getXsdContent(), sqlColumns);

            // Enrichir la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("applicable",          true);
            response.put("xsdFields",           analysis.getXsdFields());
            response.put("sqlColumns",          analysis.getSqlColumns());
            response.put("autoMapped",          analysis.getAutoMapped());
            response.put("unmappedXsdFields",   analysis.getUnmappedXsdFields());
            response.put("unmappedSqlColumns",  analysis.getUnmappedSqlColumns());
            response.put("compatibilityScore",  analysis.getCompatibilityScore());
            response.put("summary",             analysis.getSummary());
            response.put("declarationTypeCode", type.getCode());
            response.put("declarationTypeNom",  type.getNom());

            log.info("✅ Analyse terminée — score: {}%", analysis.getCompatibilityScore());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erreur analyse mapping: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    // ✅ GENERATE WITH MAPPING — génération XML avec mapping validé
    // POST /api/declarations/generate-with-mapping
    @PostMapping("/generate-with-mapping")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<?> generateWithMapping(
            @RequestBody GenerateWithMappingRequest req) {

        log.info("🚀 Génération avec mapping — Type: {}, Période: {}, {} mappings",
                req.getDeclarationTypeId(), req.getPeriode(),
                req.getMappings() != null ? req.getMappings().size() : 0);

        try {
            if (req.getMappings() == null || req.getMappings().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Le mapping est obligatoire pour ce mode de génération."
                ));
            }

            // Validation : champs obligatoires sans mapping
            long requiredUnmapped = req.getMappings().stream()
                    .filter(m -> m.isRequired()
                            && m.getSource() == XsdSqlMappingRequest.MappingSource.NONE)
                    .count();

            if (requiredUnmapped > 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", requiredUnmapped + " champ(s) obligatoire(s) du XSD n'ont pas de valeur assignée (ni SQL ni statique).",
                        "type", "REQUIRED_FIELDS_MISSING"
                ));
            }

            Declaration saved = declarationService.generateAndSaveWithMapping(
                    req.getDeclarationTypeId(),
                    req.getPeriode(),
                    req.getDateDebut(),
                    req.getDateFin(),
                    req.getMappings()
            );

            declarationService.notifyJiraTicketCreation(saved.getId(), getCurrentUsername());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("❌ Erreur génération avec mapping: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    // GET MY DECLARATIONS
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Declaration>> getMyDeclarations() {
        return ResponseEntity.ok(declarationService.getMyDeclarations());
    }
    // GET ALL
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'AUDITOR', 'INTERNAL')")
    public ResponseEntity<List<Declaration>> getAllDeclarations() {
        return ResponseEntity.ok(declarationService.getAllDeclarations());
    }
    // GET BY ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL', 'AUDITOR')")
    public ResponseEntity<Declaration> getDeclarationById(@PathVariable Long id) {
        return ResponseEntity.ok(declarationService.findById(id));
    }
    // UPDATE
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Declaration> updateDeclaration(
            @PathVariable Long id,
            @RequestBody GenerateDeclarationRequest req) {
        return ResponseEntity.ok(declarationService.updateDeclaration(id, req));
    }
    // DELETE
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Void> deleteDeclaration(@PathVariable Long id) {
        declarationService.deleteDeclaration(id);
        return ResponseEntity.noContent().build();
    }
    // UPDATE STATUT
    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'INTERNAL', 'AGENT')")
    public ResponseEntity<Declaration> updateStatut(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestParam(required = false) String commentaire,
            @RequestParam(required = false) String validePar) {
        return ResponseEntity.ok(declarationService.updateStatut(id, statut, commentaire, validePar));
    }

    @PatchMapping("/{id}/content")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<?> patchXmlContent(
            @PathVariable Long id,
            @RequestBody PatchContentRequest req) {

        log.info("✏️ Patch XML content — ID: {}", id);
        try {
            Declaration declaration = declarationService.findById(id);

            // Seules les déclarations REJETEE ou GENEREE sont modifiables
            if (declaration.getStatut() != Declaration.DeclarationStatut.GENEREE &&
                    declaration.getStatut() != Declaration.DeclarationStatut.REJETEE) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Seules les déclarations GENEREE ou REJETEE peuvent être modifiées."
                ));
            }

            // Validation XML basique
            if (req.getXmlContent() == null || req.getXmlContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Contenu XML vide"));
            }

            Declaration updated = declarationService.patchContent(id, req.getXmlContent());
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            log.error("❌ Erreur patch content: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // DTO pour le patch de contenu
    public static class PatchContentRequest {
        private String xmlContent;
        public String getXmlContent()          { return xmlContent; }
        public void   setXmlContent(String v)  { xmlContent = v; }
    }
    // STATS
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<DeclarationService.DeclarationStats> getStats() {
        return ResponseEntity.ok(declarationService.getStats());
    }
    // DOWNLOAD
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER', 'ADMIN', 'INTERNAL', 'AUDITOR')")
    public ResponseEntity<byte[]> downloadDeclaration(@PathVariable Long id) {
        Declaration d = declarationService.findById(id);
        if (d.getContenuFichier() == null || d.getContenuFichier().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] fileBytes = d.getContenuFichier().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename  = d.getNomFichier() != null ? d.getNomFichier() : "declaration_" + id;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, resolveContentType(filename))
                .body(fileBytes);
    }

    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv"))  return "text/csv";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        return "application/xml";
    }
    // DTOs internes
    /** DTO pour l'analyse de mapping */
    public static class AnalyzeMappingRequest {
        private Long      declarationTypeId;
        private LocalDate dateDebut;
        private LocalDate dateFin;

        public Long      getDeclarationTypeId()       { return declarationTypeId; }
        public void      setDeclarationTypeId(Long v) { declarationTypeId = v; }
        public LocalDate getDateDebut()               { return dateDebut; }
        public void      setDateDebut(LocalDate v)    { dateDebut = v; }
        public LocalDate getDateFin()                 { return dateFin; }
        public void      setDateFin(LocalDate v)      { dateFin = v; }
    }

    /** DTO pour la génération avec mapping */
    public static class GenerateWithMappingRequest {
        private Long      declarationTypeId;
        private String    periode;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private List<XsdSqlMappingRequest.FieldMapping> mappings;

        public Long      getDeclarationTypeId()                       { return declarationTypeId; }
        public void      setDeclarationTypeId(Long v)                 { declarationTypeId = v; }
        public String    getPeriode()                                 { return periode; }
        public void      setPeriode(String v)                         { periode = v; }
        public LocalDate getDateDebut()                               { return dateDebut; }
        public void      setDateDebut(LocalDate v)                    { dateDebut = v; }
        public LocalDate getDateFin()                                 { return dateFin; }
        public void      setDateFin(LocalDate v)                      { dateFin = v; }
        public List<XsdSqlMappingRequest.FieldMapping> getMappings()  { return mappings; }
        public void      setMappings(List<XsdSqlMappingRequest.FieldMapping> v) { mappings = v; }
    }
}
