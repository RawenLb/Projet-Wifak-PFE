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

import java.util.Map;
import java.util.Optional;

@Service
public class JiraIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JiraIntegrationService.class);

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    private final JiraApiService jiraApiService;
    private final JiraTicketLinkRepository repository;
    private final DeclarationFeignClient declarationClient;

    public JiraIntegrationService(JiraApiService jiraApiService,
                                  JiraTicketLinkRepository repository,
                                  DeclarationFeignClient declarationClient) {
        this.jiraApiService = jiraApiService;
        this.repository = repository;
        this.declarationClient = declarationClient;
    }

    // ================= CREATE =================
    @Transactional
    public JiraTicketResponseDTO createTicketForDeclaration(Long declarationId, String submittedBy) {

        Optional<JiraTicketLink> existing = repository.findByDeclarationId(declarationId);
        if (existing.isPresent()) {
            log.info("ℹ️ Ticket déjà existant pour déclaration {}", declarationId);
            return toResponse(existing.get(), "Already exists");
        }

        DeclarationDTO decl = declarationClient.getDeclarationById(declarationId);

        String summary     = "[BCT] " + decl.getDeclarationType().getNom() + " — " + decl.getPeriode();
        String description = "Declaration ID: " + declarationId;

        // Créer le ticket dans Jira
        Map<String, Object> jiraResponse = jiraApiService.createTicket(summary, description);

        String ticketKey = (String) jiraResponse.get("key");
        String ticketId  = (String) jiraResponse.get("id");
        String url       = jiraBaseUrl + "/browse/" + ticketKey;

        log.info("✅ Ticket Jira créé : {} (id={})", ticketKey, ticketId);

        // ✅ Transition directe vers IN PROGRESS (id=31 — Tous états → IN PROGRESS)
        // Le ticket passe IDEA → IN PROGRESS sans passer par TO DO
        // Pour avoir TO DO : modifier la transition Create(1) dans le workflow Jira
        // pour qu'elle démarre en TO DO au lieu de IDEA
        try {
            String transitionId = jiraApiService.mapBctStatutToTransitionId("EN_VALIDATION");
            jiraApiService.transitionTicket(ticketId, transitionId);
            log.info("🔄 Ticket {} transitionné vers IN PROGRESS", ticketKey);
        } catch (Exception e) {
            log.warn("⚠️ Transition IN PROGRESS échouée pour {} : {}", ticketKey, e.getMessage());
        }

        JiraTicketLink link = new JiraTicketLink();
        link.setDeclarationId(declarationId);
        link.setJiraTicketKey(ticketKey);
        link.setJiraTicketId(ticketId);
        link.setJiraTicketUrl(url);
        link.setJiraStatus("IN_PROGRESS");
        link.setBctStatut("EN_VALIDATION");
        link.setCreePar(submittedBy);

        repository.save(link);

        return toResponse(link, "Created");
    }

    // ================= TRANSITION =================
    @Transactional
    public JiraTicketResponseDTO transitionTicket(TransitionTicketRequest req) {

        JiraTicketLink link = repository.findByDeclarationId(req.getDeclarationId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        String statutPourTransition = req.getNewBctStatut();

        // Resoumission : REJETEE → EN_VALIDATION → transition id=3 (REJETÉE → IN PROGRESS)
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