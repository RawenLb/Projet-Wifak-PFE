package com.example.bctbackend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_sender",    columnList = "sender_id"),
    @Index(name = "idx_chat_recipient", columnList = "recipient_id"),
    @Index(name = "idx_chat_conv",      columnList = "sender_id, recipient_id, sent_at")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false, length = 255)
    private String senderId;

    @Column(name = "sender_name", nullable = false, length = 255)
    private String senderName;

    @Column(name = "sender_role", length = 50)
    private String senderRole;

    @Column(name = "recipient_id", nullable = false, length = 255)
    private String recipientId;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    public enum MessageType { TEXT, FILE, IMAGE, VOICE, SYSTEM, MISSED_CALL, CALL_ENDED }

    // ── Constructors ──────────────────────────────────────────────

    public ChatMessage() {}

    // ── Getters / Setters ─────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    // ── Builder ───────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long          id;
        private String        senderId;
        private String        senderName;
        private String        senderRole;
        private String        recipientId;
        private String        recipientName;
        private String        content;
        private MessageType   type      = MessageType.TEXT;
        private String        fileName;
        private String        fileUrl;
        private LocalDateTime sentAt    = LocalDateTime.now();
        private LocalDateTime readAt;
        private Boolean       isRead    = false;

        public Builder id(Long id)                         { this.id = id; return this; }
        public Builder senderId(String v)                  { this.senderId = v; return this; }
        public Builder senderName(String v)                { this.senderName = v; return this; }
        public Builder senderRole(String v)                { this.senderRole = v; return this; }
        public Builder recipientId(String v)               { this.recipientId = v; return this; }
        public Builder recipientName(String v)             { this.recipientName = v; return this; }
        public Builder content(String v)                   { this.content = v; return this; }
        public Builder type(MessageType v)                 { this.type = v; return this; }
        public Builder fileName(String v)                  { this.fileName = v; return this; }
        public Builder fileUrl(String v)                   { this.fileUrl = v; return this; }
        public Builder sentAt(LocalDateTime v)             { this.sentAt = v; return this; }
        public Builder readAt(LocalDateTime v)             { this.readAt = v; return this; }
        public Builder isRead(Boolean v)                   { this.isRead = v; return this; }

        public ChatMessage build() {
            ChatMessage m = new ChatMessage();
            m.id            = this.id;
            m.senderId      = this.senderId;
            m.senderName    = this.senderName;
            m.senderRole    = this.senderRole;
            m.recipientId   = this.recipientId;
            m.recipientName = this.recipientName;
            m.content       = this.content;
            m.type          = this.type;
            m.fileName      = this.fileName;
            m.fileUrl       = this.fileUrl;
            m.sentAt        = this.sentAt;
            m.readAt        = this.readAt;
            m.isRead        = this.isRead;
            return m;
        }
    }
}
