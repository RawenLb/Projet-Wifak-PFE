package com.wifak.chatservice.dto;

import com.wifak.chatservice.entities.ChatMessage;
import java.time.LocalDateTime;

public class ChatMessageDTO {

    private Long id;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String recipientId;
    private String recipientName;
    private String content;
    private String type;
    private String fileName;
    private String fileUrl;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private Boolean isRead;
    private LocalDateTime editedAt;
    private Boolean isDeleted;
    private Boolean isForwarded;

    public ChatMessageDTO() {}

    public static ChatMessageDTO from(ChatMessage m) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.id = m.getId(); dto.senderId = m.getSenderId(); dto.senderName = m.getSenderName();
        dto.senderRole = m.getSenderRole(); dto.recipientId = m.getRecipientId();
        dto.recipientName = m.getRecipientName();
        // Si le message est supprimé, masquer le contenu
        dto.content = Boolean.TRUE.equals(m.getIsDeleted()) ? "" : m.getContent();
        dto.type = m.getType() != null ? m.getType().name() : "TEXT";
        dto.fileName = Boolean.TRUE.equals(m.getIsDeleted()) ? null : m.getFileName();
        dto.fileUrl  = Boolean.TRUE.equals(m.getIsDeleted()) ? null : m.getFileUrl();
        dto.sentAt = m.getSentAt(); dto.readAt = m.getReadAt(); dto.isRead = m.getIsRead();
        dto.editedAt = m.getEditedAt();
        dto.isDeleted = m.getIsDeleted();
        dto.isForwarded = m.getIsForwarded();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String v) { this.senderId = v; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String v) { this.senderName = v; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String v) { this.senderRole = v; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String v) { this.recipientId = v; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String v) { this.recipientName = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String v) { this.fileUrl = v; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime v) { this.sentAt = v; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime v) { this.readAt = v; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean v) { this.isRead = v; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime v) { this.editedAt = v; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean v) { this.isDeleted = v; }
    public Boolean getIsForwarded() { return isForwarded; }
    public void setIsForwarded(Boolean v) { this.isForwarded = v; }
}
