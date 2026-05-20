package com.wifak.jiraintegrationservice.service;

import com.wifak.jiraintegrationservice.dto.*;
import com.wifak.jiraintegrationservice.entities.JiraTicketLink;
import com.wifak.jiraintegrationservice.feign.DeclarationFeignClient;
import com.wifak.jiraintegrationservice.repository.JiraTicketLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
public class JiraIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JiraIntegrationService.class);

    // Formateur pour la date de génération : "10/05/2025"
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    private final JiraApiService           jiraApiService;
    private final JiraTicketLinkRepository repository;
    private final DeclarationFeignClient   declarationClient;

    public JiraIntegrationService(JiraApiService jiraApiService,
                                  JiraTicketLinkRepository repository,
                                  DeclarationFeignClient declarationClient) {
        this.jiraApiService    = jiraApiService;
        this.repository        = repository;
        this.declarationClient = declarationClient;
    }

    // ================= CREATE =================
    @Transactional
    public JiraTicketResponseDTO createTicketForDeclaration(Long declarationId,
                                                            String submittedBy) {
        // Idempotence — si ticket déjà créé, retourner l'existant
        Optional<JiraTicketLink> existing = repository.findByDeclarationId(declarationId);
        if (existing.isPresent()) {
            log.info("ℹ️ Ticket déjà existant pour déclaration {}", declarationId);
            return toResponse(existing.get(), "Already exists");
        }

        DeclarationDTO decl = declarationClient.getDeclarationById(declarationId);

        // ✅ Titre : "BCT_01 - Avril 2025"  (code du type + période)
        String typeCode = decl.getDeclarationType() != null
                ? decl.getDeclarationType().getCode()   // ex. "BCT_01"
                : "BCT";
        String summary = typeCode + " - "  + decl.getDeclarationType().getNom() + " — " + decl.getPeriode();  // ex. "BCT_01 - Avril 2025"

        // ✅ Date génération formatée : "10/05/2025"
        String dateGen = decl.getDateGeneration() != null
                ? decl.getDateGeneration().format(DATE_FMT)
                : "N/A";

        // ✅ Lien fichier XML (nomFichier ou xmlFileUrl selon ce que le service expose)
        String xmlLien = decl.getXmlFileUrl() != null && !decl.getXmlFileUrl().isBlank()
                ? decl.getXmlFileUrl()
                : (decl.getNomFichier() != null ? decl.getNomFichier() : "Non disponible");

        // ✅ Responsable à assigner dans Jira
        String responsableUsername = decl.getResponsableUsername();

        // ✅ Description structurée conforme au cahier des charges
        String description =
                "Période          : " + decl.getPeriode()   + "\n" +
                        "Généré par       : " + submittedBy          + "\n" +
                        "Date génération  : " + dateGen              + "\n" +
                        "Lien fichier XML : " + xmlLien;

        log.info("🎫 Création ticket Jira — titre='{}' assigné='{}'", summary, responsableUsername);

        Map<String, Object> jiraResponse = jiraApiService.createTicket(
                summary, description, responsableUsername);

        String ticketKey = (String) jiraResponse.get("key");
        String ticketId  = (String) jiraResponse.get("id");
        String url       = jiraBaseUrl + "/browse/" + ticketKey;

        log.info("✅ Ticket Jira créé en TO DO : {} (id={})", ticketKey, ticketId);

        JiraTicketLink link = new JiraTicketLink();
        link.setDeclarationId(declarationId);
        link.setJiraTicketKey(ticketKey);
        link.setJiraTicketId(ticketId);
        link.setJiraTicketUrl(url);
        link.setJiraStatus("TO_DO");
        link.setBctStatut("GENEREE");
        link.setCreePar(submittedBy);

        repository.save(link);

        return toResponse(link, "Created");
    }

    // ================= TRANSITION =================
    @Transactional
    public JiraTicketResponseDTO transitionTicket(TransitionTicketRequest req) {

        JiraTicketLink link = repository.findByDeclarationId(req.getDeclarationId())
                .orElseThrow(() -> new RuntimeException(
                        "Ticket Jira introuvable pour déclaration : " + req.getDeclarationId()));

        String statutPourTransition = req.getNewBctStatut();

        // Resoumission : REJETEE → EN_VALIDATION → utiliser transition RESOUMISE (id=3)
        if ("EN_VALIDATION".equals(req.getNewBctStatut())
                && "REJETEE".equals(link.getBctStatut())) {
            statutPourTransition = "RESOUMISE";
            log.info("🔁 Resoumission détectée pour déclaration {}", req.getDeclarationId());
        }

        String transitionId = jiraApiService.mapBctStatutToTransitionId(statutPourTransition);
        jiraApiService.transitionTicket(link.getJiraTicketId(), transitionId);
        log.info("🔄 Ticket {} transitionné → {} (transitionId={})",
                link.getJiraTicketKey(), req.getNewBctStatut(), transitionId);

        jiraApiService.addComment(
                link.getJiraTicketId(),
                "Statut BCT changé en : " + req.getNewBctStatut()
                        + (req.getCommentaire() != null
                        ? "\nCommentaire : " + req.getCommentaire()
                        : "")
        );

        link.setBctStatut(req.getNewBctStatut());
        link.setJiraStatus(req.getNewBctStatut());
        repository.save(link);

        return toResponse(link, "Updated");
    }

    // ================= FIND =================
    public Optional<JiraTicketResponseDTO> findTicketForDeclaration(Long declarationId) {
        return repository.findByDeclarationId(declarationId)
                .map(link -> toResponse(link, "OK"));
    }

    // ================= EXISTS =================
    public boolean ticketExistsForDeclaration(Long declarationId) {
        return repository.existsByDeclarationId(declarationId);
    }

    // ================= RESPONSE =================
    private JiraTicketResponseDTO toResponse(JiraTicketLink link, String msg) {
        return new JiraTicketResponseDTO(
                link.getDeclarationId(),
                link.getJiraTicketKey(),
                link.getJiraTicketUrl(),
                link.getJiraStatus(),
                msg
        );
    }
}