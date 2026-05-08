package com.example.bctbackend.service;

import com.example.bctbackend.dto.ChatContactDTO;
import com.example.bctbackend.dto.ChatMessageDTO;
import com.example.bctbackend.dto.UserDTO;
import com.example.bctbackend.entities.ChatMessage;
import com.example.bctbackend.entities.User;
import com.example.bctbackend.repositories.ChatMessageRepository;
import com.example.bctbackend.repositories.UserRepository;
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

    private final ChatMessageRepository  msgRepo;
    private final UserRepository         userRepo;
    private final PresenceService        presenceService;
    private final KeycloakAdminService   keycloakAdminService;

    private static final Set<String> CHAT_ROLES = Set.of("ROLE_AGENT", "ROLE_MANAGER");

    public ChatService(
            ChatMessageRepository msgRepo,
            UserRepository userRepo,
            PresenceService presenceService,
            KeycloakAdminService keycloakAdminService
    ) {
        this.msgRepo              = msgRepo;
        this.userRepo             = userRepo;
        this.presenceService      = presenceService;
        this.keycloakAdminService = keycloakAdminService;
    }

    // ── Save a call-ended notification ───────────────────────────

    /**
     * Saves a CALL_ENDED system message with duration, visible to both participants.
     *
     * @param callerId    Keycloak ID of the person who ended the call
     * @param callerName  Display name of the caller
     * @param recipientId Keycloak ID of the other participant
     * @param callType    "AUDIO" or "VIDEO"
     * @param durationSec Duration of the call in seconds
     */
    @Transactional
    public ChatMessageDTO saveCallEnded(
            String callerId,
            String callerName,
            String recipientId,
            String callType,
            int durationSec
    ) {
        String typeLabel = "VIDEO".equalsIgnoreCase(callType) ? "Appel vidéo" : "Appel audio";
        String duration  = formatDuration(durationSec);
        String content   = typeLabel + " terminé · " + duration;

        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
            .senderId(callerId)
            .senderName(callerName != null ? callerName : "Unknown")
            .senderRole("")
            .recipientId(recipientId)
            .recipientName(recipientName)
            .content(content)
            .type(ChatMessage.MessageType.CALL_ENDED)
            .sentAt(LocalDateTime.now())
            .isRead(false)
            .build();

        ChatMessage saved = msgRepo.save(msg);
        log.info("[Chat] Call ended saved: {} → {} ({}, {})", callerId, recipientId, callType, duration);
        return ChatMessageDTO.from(saved);
    }

    private String formatDuration(int totalSec) {
        if (totalSec < 60) return totalSec + "s";
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return sec == 0 ? min + " min" : min + " min " + sec + "s";
    }

    // ── Save a missed call notification ──────────────────────────

    /**
     * Saves a MISSED_CALL system message visible to the recipient.
     * Called when: caller hangs up before answer, or recipient auto-rejects after timeout.
     *
     * @param callerId   Keycloak ID of the person who placed the call
     * @param callerName Display name of the caller
     * @param recipientId Keycloak ID of the person who missed the call
     * @param callType   "AUDIO" or "VIDEO"
     */
    @Transactional
    public ChatMessageDTO saveMissedCall(
            String callerId,
            String callerName,
            String recipientId,
            String callType
    ) {
        String label = "VIDEO".equalsIgnoreCase(callType) ? "Appel vidéo manqué" : "Appel audio manqué";
        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
            .senderId(callerId)
            .senderName(callerName != null ? callerName : "Unknown")
            .senderRole("")
            .recipientId(recipientId)
            .recipientName(recipientName)
            .content(label)
            .type(ChatMessage.MessageType.MISSED_CALL)
            .sentAt(LocalDateTime.now())
            .isRead(false)
            .build();

        ChatMessage saved = msgRepo.save(msg);
        log.info("[Chat] Missed call saved: {} → {} ({})", callerId, recipientId, callType);
        return ChatMessageDTO.from(saved);
    }

    // ── Save a new message ────────────────────────────────────────

    @Transactional
    public ChatMessageDTO saveMessage(
            String senderId,
            String senderName,
            String senderRole,
            String recipientId,
            String content,
            ChatMessage.MessageType type,
            String fileName,
            String fileUrl
    ) {
        // Resolve recipient display name (best-effort, don't fail if not found)
        String recipientName = resolveUserName(recipientId);

        ChatMessage msg = ChatMessage.builder()
            .senderId(senderId)
            .senderName(senderName != null ? senderName : "Unknown")
            .senderRole(senderRole)
            .recipientId(recipientId)
            .recipientName(recipientName)
            .content(content != null ? content : "")
            .type(type != null ? type : ChatMessage.MessageType.TEXT)
            .fileName(fileName)
            .fileUrl(fileUrl)
            .sentAt(LocalDateTime.now())
            .isRead(false)
            .build();

        ChatMessage saved = msgRepo.save(msg);
        log.debug("[Chat] Saved {} message {} from {} to {}", saved.getType(), saved.getId(), senderId, recipientId);
        return ChatMessageDTO.from(saved);
    }

    // ── Conversation history ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getConversation(String userId, String partnerId) {
        return msgRepo.findConversation(userId, partnerId)
            .stream()
            .map(ChatMessageDTO::from)
            .collect(Collectors.toList());
    }

    // ── Mark messages as read ─────────────────────────────────────

    @Transactional
    public void markRead(String senderId, String recipientId) {
        int updated = msgRepo.markAllRead(senderId, recipientId);
        log.debug("[Chat] Marked {} messages read from {} to {}", updated, senderId, recipientId);
    }

    @Transactional
    public void markOneRead(Long messageId) {
        msgRepo.markOneRead(messageId);
    }

    // ── Contact list ──────────────────────────────────────────────

    /**
     * Returns the list of contacts the current user can chat with.
     *  - AGENT   → sees all MANAGER users
     *  - MANAGER → sees all AGENT users
     *
     * Source of truth: Keycloak.
     * Enriched with: online status, unread count, last message preview.
     */
    @Transactional(readOnly = true)
    public List<ChatContactDTO> getContacts(String currentUserId, Set<String> currentUserRoles) {

        String targetRole = resolveTargetRole(currentUserRoles);
        if (targetRole == null) {
            log.warn("[Chat] getContacts: no chat role for user {}, roles={}", currentUserId, currentUserRoles);
            return Collections.emptyList();
        }

        log.info("[Chat] Loading contacts for {} (roles={}) → target: {}", currentUserId, currentUserRoles, targetRole);

        // 1. Fetch users with the target role from Keycloak
        List<UserDTO> keycloakUsers;
        try {
            keycloakUsers = keycloakAdminService.getUsersByRole(targetRole);
            log.info("[Chat] Keycloak returned {} users with role {}", keycloakUsers.size(), targetRole);
        } catch (Exception e) {
            log.error("[Chat] Failed to fetch users from Keycloak for role {}: {}", targetRole, e.getMessage());
            keycloakUsers = Collections.emptyList();
        }

        // 2. Filter out current user and disabled accounts
        List<UserDTO> candidates = keycloakUsers.stream()
            .filter(u -> !currentUserId.equals(u.getId()))
            .filter(UserDTO::isEnabled)
            .collect(Collectors.toList());

        log.info("[Chat] {} eligible contacts after filtering", candidates.size());

        // 3. Enrich each contact
        return candidates.stream()
            .map(u -> buildContactDTO(currentUserId, u, targetRole))
            .sorted(Comparator
                .comparingLong(ChatContactDTO::getUnread).reversed()
                .thenComparing(
                    c -> c.getLastTime() != null ? c.getLastTime() : LocalDateTime.MIN,
                    Comparator.reverseOrder()
                ))
            .collect(Collectors.toList());
    }

    private ChatContactDTO buildContactDTO(String currentUserId, UserDTO partner, String targetRole) {
        String partnerId = partner.getId();

        // Unread count
        long unread = 0;
        try {
            unread = msgRepo.countUnread(partnerId, currentUserId);
        } catch (Exception e) {
            log.warn("[Chat] Could not count unread for {}: {}", partnerId, e.getMessage());
        }

        // Last message preview
        String lastMsg = null;
        LocalDateTime lastTime = null;
        try {
            Optional<ChatMessage> latest = msgRepo.findLatestMessage(currentUserId, partnerId);
            if (latest.isPresent()) {
                lastMsg  = latest.get().getContent();
                lastTime = latest.get().getSentAt();
            }
        } catch (Exception e) {
            log.warn("[Chat] Could not fetch latest message for {}: {}", partnerId, e.getMessage());
        }

        // Display name
        String firstName = partner.getFirstName() != null ? partner.getFirstName() : "";
        String lastName  = partner.getLastName()  != null ? partner.getLastName()  : "";
        String fullName  = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) fullName = partner.getUsername();

        String roleLabel = targetRole.replace("ROLE_", "");

        return ChatContactDTO.builder()
            .id(partnerId)
            .username(partner.getUsername())
            .fullName(fullName)
            .role(roleLabel)
            .online(presenceService.isOnline(partnerId))
            .unread(unread)
            .lastMsg(lastMsg)
            .lastTime(lastTime)
            .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String resolveTargetRole(Set<String> roles) {
        if (roles.contains("ROLE_AGENT"))   return "ROLE_MANAGER";
        if (roles.contains("ROLE_MANAGER")) return "ROLE_AGENT";
        return null;
    }

    private String resolveUserName(String userId) {
        // Try local DB first
        Optional<User> dbUser = userRepo.findByKeycloakId(userId);
        if (dbUser.isPresent()) {
            User u = dbUser.get();
            String name = ((u.getFirstName() != null ? u.getFirstName() : "")
                + " " + (u.getLastName() != null ? u.getLastName() : "")).trim();
            return name.isEmpty() ? u.getUsername() : name;
        }
        // Fallback to Keycloak
        try {
            UserDTO dto = keycloakAdminService.getUserById(userId);
            String name = ((dto.getFirstName() != null ? dto.getFirstName() : "")
                + " " + (dto.getLastName() != null ? dto.getLastName() : "")).trim();
            return name.isEmpty() ? dto.getUsername() : name;
        } catch (Exception e) {
            log.warn("[Chat] Could not resolve name for user {}: {}", userId, e.getMessage());
            return userId;
        }
    }
}
