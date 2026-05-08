package com.example.bctbackend.dto;

import com.example.bctbackend.entities.ChatMessage;
import java.time.LocalDateTime;

public class ChatMessageDTO {

    private Long          id;
    private String        senderId;
    private String        senderName;
    private String        senderRole;
    private String        recipientId;
    private String        recipientName;
    private String        content;
    private String        type;
    private String        fileName;
    private String        fileUrl;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private Boolean       isRead;

    // ── Constructors ──────────────────────────────────────────────

    public ChatMessageDTO() {}

    // ── Static factory ────────────────────────────────────────────

    public static ChatMessageDTO from(ChatMessage m) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.id            = m.getId();
        dto.senderId      = m.getSenderId();
        dto.senderName    = m.getSenderName();
        dto.senderRole    = m.getSenderRole();
        dto.recipientId   = m.getRecipientId();
        dto.recipientName = m.getRecipientName();
        dto.content       = m.getContent();
        dto.type          = m.getType() != null ? m.getType().name() : "TEXT";
        dto.fileName      = m.getFileName();
        dto.fileUrl       = m.getFileUrl();
        dto.sentAt        = m.getSentAt();
        dto.readAt        = m.getReadAt();
        dto.isRead        = m.getIsRead();
        return dto;
    }

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
        private String        type;
        private String        fileName;
        private String        fileUrl;
        private LocalDateTime sentAt;
        private LocalDateTime readAt;
        private Boolean       isRead;

        public Builder id(Long v)                  { this.id = v; return this; }
        public Builder senderId(String v)          { this.senderId = v; return this; }
        public Builder senderName(String v)        { this.senderName = v; return this; }
        public Builder senderRole(String v)        { this.senderRole = v; return this; }
        public Builder recipientId(String v)       { this.recipientId = v; return this; }
        public Builder recipientName(String v)     { this.recipientName = v; return this; }
        public Builder content(String v)           { this.content = v; return this; }
        public Builder type(String v)              { this.type = v; return this; }
        public Builder fileName(String v)          { this.fileName = v; return this; }
        public Builder fileUrl(String v)           { this.fileUrl = v; return this; }
        public Builder sentAt(LocalDateTime v)     { this.sentAt = v; return this; }
        public Builder readAt(LocalDateTime v)     { this.readAt = v; return this; }
        public Builder isRead(Boolean v)           { this.isRead = v; return this; }

        public ChatMessageDTO build() {
            ChatMessageDTO dto = new ChatMessageDTO();
            dto.id            = this.id;
            dto.senderId      = this.senderId;
            dto.senderName    = this.senderName;
            dto.senderRole    = this.senderRole;
            dto.recipientId   = this.recipientId;
            dto.recipientName = this.recipientName;
            dto.content       = this.content;
            dto.type          = this.type;
            dto.fileName      = this.fileName;
            dto.fileUrl       = this.fileUrl;
            dto.sentAt        = this.sentAt;
            dto.readAt        = this.readAt;
            dto.isRead        = this.isRead;
            return dto;
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getSenderId()                { return senderId; }
    public void setSenderId(String v)          { this.senderId = v; }

    public String getSenderName()              { return senderName; }
    public void setSenderName(String v)        { this.senderName = v; }

    public String getSenderRole()              { return senderRole; }
    public void setSenderRole(String v)        { this.senderRole = v; }

    public String getRecipientId()             { return recipientId; }
    public void setRecipientId(String v)       { this.recipientId = v; }

    public String getRecipientName()           { return recipientName; }
    public void setRecipientName(String v)     { this.recipientName = v; }

    public String getContent()                 { return content; }
    public void setContent(String v)           { this.content = v; }

    public String getType()                    { return type; }
    public void setType(String v)              { this.type = v; }

    public String getFileName()                { return fileName; }
    public void setFileName(String v)          { this.fileName = v; }

    public String getFileUrl()                 { return fileUrl; }
    public void setFileUrl(String v)           { this.fileUrl = v; }

    public LocalDateTime getSentAt()           { return sentAt; }
    public void setSentAt(LocalDateTime v)     { this.sentAt = v; }

    public LocalDateTime getReadAt()           { return readAt; }
    public void setReadAt(LocalDateTime v)     { this.readAt = v; }

    public Boolean getIsRead()                 { return isRead; }
    public void setIsRead(Boolean v)           { this.isRead = v; }
}
