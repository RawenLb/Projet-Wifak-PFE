package com.wifak.chatservice.service;

import com.wifak.chatservice.dto.ChatContactDTO;
import com.wifak.chatservice.dto.ChatMessageDTO;
import com.wifak.chatservice.dto.UserDTO;
import com.wifak.chatservice.entities.ChatMessage;
import com.wifak.chatservice.feign.AuthentificationFeignClient;
import com.wifak.chatservice.repositories.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository msgRepo;
    private final PresenceService       presenceService;
    private final AuthentificationFeignClient authClient;

    public ChatService(ChatMessageRepository msgRepo,
                       PresenceService presenceService,
                       AuthentificationFeignClient authClient) {
        this.msgRepo         = msgRepo;
        this.presenceService  = presenceService;
        this.authClient       = authClient;
    }

    // ── Edit message ──────────────────────────────────────────────

    @Transactional
    public ChatMessageDTO editMessage(Long messageId, String requesterId, String newContent) {
        ChatMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        if (!msg.getSenderId().equals(requesterId)) {
            throw new SecurityException("Not allowed to edit this message");
        }
        if (Boolean.TRUE.equals(msg.getIsDeleted())) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }
        msg.setContent(newContent);
        msg.setEditedAt(LocalDateTime.now());
        return ChatMessageDTO.from(msgRepo.save(msg));
    }

    // ── Delete message ────────────────────────────────────────────

    @Transactional
    public ChatMessageDTO deleteMessage(Long messageId, String requesterId) {
        ChatMessage msg = msgRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        if (!msg.getSenderId().equals(requesterId)) {
            throw new SecurityException("Not allowed to delete this message");
        }
        msg.setIsDeleted(true);
        msg.setDeletedAt(LocalDateTime.now());
        msg.setContent("");
        msg.setFileName(null);
        msg.setFileUrl(null);
        return ChatMessageDTO.from(msgRepo.save(msg));
    }

    // ── Forward message ───────────────────────────────────────────

    @Transactional
    public ChatMessageDTO forwardMessage(Long messageId, String forwarderId,
                                          String forwarderName, String forwarderRole,
                                          String targetId) {
        ChatMessage original = msgRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        if (Boolean.TRUE.equals(original.getIsDeleted())) {
            throw new IllegalStateException("Cannot forward a deleted message");
        }
        String recipientName = resolveUserName(targetId);
        ChatMessage forwarded = ChatMessage.builder()
                .senderId(forwarderId).senderName(forwarderName != null ? forwarderName : "Unknown")
                .senderRole(forwarderRole).recipientId(targetId).recipientName(recipientName)
                .content(original.getContent()).type(original.getType())
                .fileName(original.getFileName()).fileUrl(original.getFileUrl())
                .sentAt(LocalDateTime.now()).isRead(false).isForwarded(true).build();
        return ChatMessageDTO.from(msgRepo.save(forwarded));
    }

    // ── Save call-ended ───────────────────────────────────────────

    @Transactional
    public ChatMessageDTO saveCallEnded(String callerId, String callerName,
                                        String recipientId, String callType, int durationSec) {
        String typeLabel     = "VIDEO".equalsIgnoreCase(callType) ? "Appel vidéo" : "Appel audio";
        String content       = typeLabel + " terminé · " + formatDuration(durationSec);
        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
                .senderId(callerId).senderName(callerName != null ? callerName : "Unknown")
                .senderRole("").recipientId(recipientId).recipientName(recipientName)
                .content(content).type(ChatMessage.MessageType.CALL_ENDED)
                .sentAt(LocalDateTime.now()).isRead(false).build();

        return ChatMessageDTO.from(msgRepo.save(msg));
    }

    // ── Save missed call ──────────────────────────────────────────

    @Transactional
    public ChatMessageDTO saveMissedCall(String callerId, String callerName,
                                         String recipientId, String callType) {
        String label         = "VIDEO".equalsIgnoreCase(callType)
                ? "Appel vidéo manqué" : "Appel audio manqué";
        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
                .senderId(callerId).senderName(callerName != null ? callerName : "Unknown")
                .senderRole("").recipientId(recipientId).recipientName(recipientName)
                .content(label).type(ChatMessage.MessageType.MISSED_CALL)
                .sentAt(LocalDateTime.now()).isRead(false).build();

        return ChatMessageDTO.from(msgRepo.save(msg));
    }

    // ── Save message ──────────────────────────────────────────────

    @Transactional
    public ChatMessageDTO saveMessage(String senderId, String senderName, String senderRole,
                                      String recipientId, String content,
                                      ChatMessage.MessageType type,
                                      String fileName, String fileUrl) {
        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
                .senderId(senderId).senderName(senderName != null ? senderName : "Unknown")
                .senderRole(senderRole).recipientId(recipientId).recipientName(recipientName)
                .content(content != null ? content : "")
                .type(type != null ? type : ChatMessage.MessageType.TEXT)
                .fileName(fileName).fileUrl(fileUrl)
                .sentAt(LocalDateTime.now()).isRead(false).build();

        return ChatMessageDTO.from(msgRepo.save(msg));
    }

    // ── Conversation history ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getConversation(String userId, String partnerId) {
        return msgRepo.findConversation(userId, partnerId)
                .stream().map(ChatMessageDTO::from).collect(Collectors.toList());
    }

    // ── Mark read ─────────────────────────────────────────────────

    @Transactional
    public void markRead(String senderId, String recipientId) {
        msgRepo.markAllRead(senderId, recipientId);
    }

    // ── Contact list ──────────────────────────────────────────────

    /**
     * Tous les rôles chat (AGENT, MANAGER, ADMIN) voient
     * tous les autres utilisateurs (AGENT + MANAGER + ADMIN),
     * sauf eux-mêmes.
     */
    @Transactional(readOnly = true)
    public List<ChatContactDTO> getContacts(String currentUserId, Set<String> currentUserRoles) {

        boolean hasChatRole = currentUserRoles.contains("ROLE_AGENT")
                || currentUserRoles.contains("ROLE_MANAGER")
                || currentUserRoles.contains("ROLE_ADMIN");

        if (!hasChatRole) {
            log.warn("[Chat] getContacts: no chat role for user {}", currentUserId);
            return Collections.emptyList();
        }

        // Fetch users for all three roles, deduplicate by id
        List<String> rolesToFetch = List.of("ROLE_AGENT", "ROLE_MANAGER", "ROLE_ADMIN");
        Map<String, UserDTO> uniqueUsers = new LinkedHashMap<>();

        for (String role : rolesToFetch) {
            try {
                List<UserDTO> users = authClient.getUsersByRole(role);
                if (users != null) {
                    users.forEach(u -> {
                        if (u != null && u.getId() != null) {
                            uniqueUsers.putIfAbsent(u.getId(), u);
                        }
                    });
                }
                log.info("[Chat] Fetched {} users for role {}",
                        users != null ? users.size() : 0, role);
            } catch (Exception e) {
                log.error("[Chat] Failed to fetch users for role {}: {}", role, e.getMessage());
            }
        }

        log.info("[Chat] Total unique users before filter: {}", uniqueUsers.size());

        List<ChatContactDTO> contacts = uniqueUsers.values().stream()
                .filter(u -> !currentUserId.equals(u.getId()))
                .filter(UserDTO::isEnabled)
                .map(u -> buildContactDTO(currentUserId, u))
                .sorted(Comparator
                        .comparingLong(ChatContactDTO::getUnread).reversed()
                        .thenComparing(
                                c -> c.getLastTime() != null ? c.getLastTime() : LocalDateTime.MIN,
                                Comparator.reverseOrder()
                        ))
                .collect(Collectors.toList());

        log.info("[Chat] Returning {} contacts for user {}", contacts.size(), currentUserId);
        return contacts;
    }

    // ── Build contact DTO ─────────────────────────────────────────

    private ChatContactDTO buildContactDTO(String currentUserId, UserDTO partner) {
        String partnerId = partner.getId();

        long unread = 0;
        try {
            unread = msgRepo.countUnread(partnerId, currentUserId);
        } catch (Exception ignored) {}

        String lastMsg         = null;
        LocalDateTime lastTime = null;
        try {
            var page = msgRepo.findLatestMessagePage(
                    currentUserId, partnerId,
                    org.springframework.data.domain.PageRequest.of(0, 1)
            );
            if (page != null && !page.isEmpty()) {
                ChatMessage latest = page.getContent().get(0);
                lastMsg  = latest.getContent();
                lastTime = latest.getSentAt();
            }
        } catch (Exception ignored) {}

        String firstName = partner.getFirstName() != null ? partner.getFirstName() : "";
        String lastName  = partner.getLastName()  != null ? partner.getLastName()  : "";
        String fullName  = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) fullName = partner.getUsername();

        String role = resolveDisplayRole(partner.getRoles());

        return ChatContactDTO.builder()
                .id(partnerId)
                .username(partner.getUsername())
                .fullName(fullName)
                .role(role)
                .online(presenceService.isOnline(partnerId))
                .unread(unread)
                .lastMsg(lastMsg)
                .lastTime(lastTime)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String resolveDisplayRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "USER";
        if (roles.contains("ROLE_ADMIN"))   return "ADMIN";
        if (roles.contains("ROLE_MANAGER")) return "MANAGER";
        if (roles.contains("ROLE_AGENT"))   return "AGENT";
        return "USER";
    }

    private String resolveUserName(String userId) {
        try {
            UserDTO dto  = authClient.getUserById(userId);
            String name  = (
                    (dto.getFirstName() != null ? dto.getFirstName() : "") + " " +
                            (dto.getLastName()  != null ? dto.getLastName()  : "")
            ).trim();
            return name.isEmpty() ? dto.getUsername() : name;
        } catch (Exception e) {
            log.warn("[Chat] Could not resolve name for userId={}: {}", userId, e.getMessage());
            return userId;
        }
    }

    private String formatDuration(int totalSec) {
        if (totalSec < 60) return totalSec + "s";
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return sec == 0 ? min + " min" : min + " min " + sec + "s";
    }
}