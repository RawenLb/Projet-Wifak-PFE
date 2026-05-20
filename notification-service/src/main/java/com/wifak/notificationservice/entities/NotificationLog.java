package com.wifak.notificationservice.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Trace chaque email envoyé par le notification-service.
 * Stocké dans la base wifak_notification.
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long declarationId;

    /**
     * Type : PENDING_VALIDATION | REJECTION | DEADLINE_ALERT
     */
    @Column(nullable = false, length = 30)
    private String notificationType;

    /** Email ou username du destinataire */
    @Column(nullable = false)
    private String recipient;

    /** OK | ERROR */
    @Column(nullable = false, length = 10)
    private String statut;

    /** Détail (ex: "J+2" pour deadline, message d'erreur pour ERROR) */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private LocalDateTime dateSent;

    @PrePersist
    protected void onCreate() {
        dateSent = LocalDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }

    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long declarationId) { this.declarationId = declarationId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public LocalDateTime getDateSent() { return dateSent; }
    public void setDateSent(LocalDateTime dateSent) { this.dateSent = dateSent; }
}