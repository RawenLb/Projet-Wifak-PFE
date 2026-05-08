package com.example.bctbackend.config;

import com.example.bctbackend.dto.ChatMessageDTO;
import com.example.bctbackend.dto.WsEnvelope;
import com.example.bctbackend.entities.ChatMessage;
import com.example.bctbackend.service.ChatService;
import com.example.bctbackend.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService     chatService;
    private final PresenceService presenceService;

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ChatWebSocketHandler(ChatService chatService, PresenceService presenceService) {
        this.chatService     = chatService;
        this.presenceService = presenceService;
    }

    // ── Connection lifecycle ──────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        presenceService.register(userId, session);
        log.info("[Chat WS] Connected: {} ({})", getUserName(session), userId);
        broadcastPresence(userId, getUserName(session), true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        if (userId == null) return;

        presenceService.unregister(userId, session);
        log.info("[Chat WS] Disconnected: {} ({})", getUserName(session), userId);

        if (!presenceService.isOnline(userId)) {
            broadcastPresence(userId, getUserName(session), false);
        }
    }

    // ── Message handling ──────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        if (userId == null) return;

        try {
            WsEnvelope envelope = mapper.readValue(message.getPayload(), WsEnvelope.class);
            String type = envelope.getType();

            if (type == null) {
                log.warn("[Chat WS] Received envelope with null type from {}", userId);
                return;
            }

            switch (type) {
                case "MESSAGE":
                    handleChatMessage(session, userId, envelope);
                    break;
                case "TYPING":
                    handleTyping(session, userId, envelope);
                    break;
                case "READ":
                    handleRead(userId, envelope);
                    break;
                case "HISTORY":
                    handleHistory(session, userId, envelope);
                    break;
                case "CALL_OFFER":
                case "CALL_ANSWER":
                case "CALL_REJECT":
                case "CALL_END":
                case "CALL_BUSY":
                case "ICE_CANDIDATE":
                    handleSignaling(userId, envelope);
                    break;
                default:
                    log.warn("[Chat WS] Unknown type: {}", type);
            }
        } catch (Exception e) {
            log.error("[Chat WS] Error processing message from {}: {}", userId, e.getMessage());
        }
    }

    // ── Handlers ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleChatMessage(WebSocketSession session, String senderId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;

        String recipientId = (String) payload.get("recipientId");
        String content     = (String) payload.get("content");
        String typeStr     = payload.containsKey("type") ? (String) payload.get("type") : "TEXT";
        String fileName    = (String) payload.get("fileName");
        String fileUrl     = (String) payload.get("fileUrl");

        if (recipientId == null) return;

        // For FILE/IMAGE/VOICE messages content may be the filename — allow it
        // Only reject if both content and fileUrl are blank (truly empty message)
        boolean hasContent = content != null && !content.isBlank();
        boolean hasFile    = fileUrl  != null && !fileUrl.isBlank();
        if (!hasContent && !hasFile) return;

        // Ensure content is never null (use filename or type as fallback)
        if (content == null || content.isBlank()) {
            content = fileName != null ? fileName : typeStr.toLowerCase();
        }

        ChatMessage.MessageType msgType;
        try {
            msgType = ChatMessage.MessageType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            msgType = ChatMessage.MessageType.TEXT;
        }

        ChatMessageDTO saved = chatService.saveMessage(
            senderId,
            getUserName(session),
            getUserRole(session),
            recipientId,
            content,
            msgType,
            fileName,
            fileUrl
        );

        WsEnvelope outEnv = new WsEnvelope("MESSAGE", saved);
        sendToUser(recipientId, outEnv);
        sendToSession(session, outEnv);
    }

    @SuppressWarnings("unchecked")
    private void handleTyping(WebSocketSession session, String senderId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;

        String  recipientId = (String)  payload.get("recipientId");
        Boolean typing      = (Boolean) payload.getOrDefault("typing", false);
        if (recipientId == null) return;

        Map<String, Object> typingPayload = Map.of(
            "senderId",    senderId,
            "senderName",  getUserName(session),
            "recipientId", recipientId,
            "typing",      typing
        );
        sendToUser(recipientId, new WsEnvelope("TYPING", typingPayload));
    }

    @SuppressWarnings("unchecked")
    private void handleRead(String recipientId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;

        String senderId = (String) payload.get("senderId");
        if (senderId == null) return;

        chatService.markRead(senderId, recipientId);

        Map<String, Object> readPayload = Map.of(
            "senderId",    senderId,
            "recipientId", recipientId
        );
        sendToUser(senderId, new WsEnvelope("READ", readPayload));
    }

    @SuppressWarnings("unchecked")
    private void handleHistory(WebSocketSession session, String userId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;

        String withUserId = (String) payload.get("withUserId");
        if (withUserId == null) return;

        List<ChatMessageDTO> history = chatService.getConversation(userId, withUserId);
        sendToSession(session, new WsEnvelope("HISTORY", history));
    }

    /**
     * WebRTC signaling relay + missed-call persistence.
     *
     * CALL_OFFER  → relay to recipient; if offline, save missed call immediately for caller
     * CALL_END    → relay + if call was never answered (caller hung up), save missed call for recipient
     * CALL_REJECT → relay + save missed call for caller (recipient rejected)
     * CALL_BUSY   → relay + save missed call for caller (recipient was busy)
     * Others      → pure relay
     */
    @SuppressWarnings("unchecked")
    private void handleSignaling(String fromUserId, WsEnvelope envelope) {
        Map<String, Object> payload = (Map<String, Object>) envelope.getPayload();
        if (payload == null) {
            log.warn("[Chat WS] Signaling {} has null payload", envelope.getType());
            return;
        }

        String toId    = (String) payload.get("toId");
        String callType = (String) payload.getOrDefault("callType", "AUDIO");
        String fromName = (String) payload.getOrDefault("fromName", "");

        if (toId == null) {
            log.warn("[Chat WS] Signaling {} missing toId", envelope.getType());
            return;
        }

        log.debug("[Chat WS] Relay {} from {} to {}", envelope.getType(), fromUserId, toId);

        switch (envelope.getType()) {

            case "CALL_OFFER": {
                boolean recipientOnline = presenceService.isOnline(toId);
                if (!recipientOnline) {
                    log.info("[Chat WS] Recipient {} offline — saving missed call", toId);
                    try {
                        ChatMessageDTO missed = chatService.saveMissedCall(fromUserId, fromName, toId, callType);
                        Map<String, Object> offlinePayload = Map.of(
                            "type", "CALL_REJECT", "callId", payload.getOrDefault("callId", ""),
                            "callType", callType, "fromId", toId, "fromName", "",
                            "toId", fromUserId, "reason", "OFFLINE"
                        );
                        sendToUser(fromUserId, new WsEnvelope("CALL_REJECT", offlinePayload));
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                    } catch (Exception e) {
                        log.error("[Chat WS] Failed to save missed call: {}", e.getMessage());
                    }
                } else {
                    sendToUser(toId, envelope);
                }
                break;
            }

            case "CALL_END": {
                // Relay to recipient first
                sendToUser(toId, envelope);

                Boolean wasAnswered = (Boolean) payload.getOrDefault("wasAnswered", false);

                if (Boolean.TRUE.equals(wasAnswered)) {
                    // Call was answered and then ended — save CALL_ENDED with duration
                    int durationSec = 0;
                    Object durObj = payload.get("durationSec");
                    if (durObj instanceof Number) {
                        durationSec = ((Number) durObj).intValue();
                    }
                    log.info("[Chat WS] Call ended (answered, {}s) — saving call-ended for both", durationSec);
                    try {
                        ChatMessageDTO ended = chatService.saveCallEnded(
                            fromUserId, fromName, toId, callType, durationSec
                        );
                        sendToUser(toId,       new WsEnvelope("MESSAGE", ended));
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", ended));
                    } catch (Exception e) {
                        log.error("[Chat WS] Failed to save call-ended: {}", e.getMessage());
                    }
                } else {
                    // Call ended before answer — save MISSED_CALL for recipient
                    log.info("[Chat WS] Call ended before answer — saving missed call for {}", toId);
                    try {
                        ChatMessageDTO missed = chatService.saveMissedCall(fromUserId, fromName, toId, callType);
                        sendToUser(toId,       new WsEnvelope("MESSAGE", missed));
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                    } catch (Exception e) {
                        log.error("[Chat WS] Failed to save missed call: {}", e.getMessage());
                    }
                }
                break;
            }

            case "CALL_REJECT":
            case "CALL_BUSY": {
                // Relay to caller
                sendToUser(toId, envelope);
                // Recipient rejected or was busy — save MISSED_CALL for caller
                log.info("[Chat WS] {} — saving missed call for caller {}", envelope.getType(), toId);
                try {
                    ChatMessageDTO missed = chatService.saveMissedCall(fromUserId, fromName, toId, callType);
                    sendToUser(toId,       new WsEnvelope("MESSAGE", missed));
                    sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                } catch (Exception e) {
                    log.error("[Chat WS] Failed to save missed call on reject: {}", e.getMessage());
                }
                break;
            }

            default:
                sendToUser(toId, envelope);
        }
    }

    // ── Presence broadcast ────────────────────────────────────────

    private void broadcastPresence(String userId, String username, boolean online) {
        Map<String, Object> presencePayload = Map.of(
            "userId",   userId,
            "username", username,
            "online",   online
        );
        WsEnvelope env = new WsEnvelope("PRESENCE", presencePayload);
        presenceService.getOnlineUserIds().forEach(uid -> sendToUser(uid, env));
    }

    // ── Send helpers ──────────────────────────────────────────────

    private void sendToUser(String userId, WsEnvelope envelope) {
        presenceService.getSessions(userId).forEach(s -> sendToSession(s, envelope));
    }

    private void sendToSession(WebSocketSession session, WsEnvelope envelope) {
        if (!session.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(envelope);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("[Chat WS] Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    // ── Session attribute helpers ─────────────────────────────────

    private String getUserId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }

    private String getUserName(WebSocketSession session) {
        Object val = session.getAttributes().get("username");
        return val != null ? (String) val : "Unknown";
    }

    private String getUserRole(WebSocketSession session) {
        Object val = session.getAttributes().get("role");
        return val != null ? (String) val : "USER";
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (Exception ignored) {}
    }
}
