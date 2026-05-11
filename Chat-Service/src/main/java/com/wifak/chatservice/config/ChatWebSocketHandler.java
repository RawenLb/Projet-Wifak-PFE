package com.wifak.chatservice.config;

import com.wifak.chatservice.dto.ChatMessageDTO;
import com.wifak.chatservice.dto.WsEnvelope;
import com.wifak.chatservice.entities.ChatMessage;
import com.wifak.chatservice.service.ChatService;
import com.wifak.chatservice.service.PresenceService;
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

    private final ChatService chatService;
    private final PresenceService presenceService;

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ChatWebSocketHandler(ChatService chatService, PresenceService presenceService) {
        this.chatService = chatService;
        this.presenceService = presenceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        if (userId == null) { closeQuietly(session); return; }
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

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        if (userId == null) return;
        try {
            WsEnvelope envelope = mapper.readValue(message.getPayload(), WsEnvelope.class);
            String type = envelope.getType();
            if (type == null) return;
            switch (type) {
                case "MESSAGE"      -> handleChatMessage(session, userId, envelope);
                case "TYPING"       -> handleTyping(session, userId, envelope);
                case "READ"         -> handleRead(userId, envelope);
                case "HISTORY"      -> handleHistory(session, userId, envelope);
                case "CALL_OFFER", "CALL_ANSWER", "CALL_REJECT", "CALL_END", "CALL_BUSY", "ICE_CANDIDATE"
                                    -> handleSignaling(userId, envelope);
                case "MSG_EDIT", "MSG_DELETE", "MSG_FORWARD"
                                    -> handleMessageAction(session, userId, envelope);
                default             -> log.warn("[Chat WS] Unknown type: {}", type);
            }
        } catch (Exception e) {
            log.error("[Chat WS] Error processing message from {}: {}", userId, e.getMessage());
        }
    }

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
        boolean hasContent = content != null && !content.isBlank();
        boolean hasFile    = fileUrl  != null && !fileUrl.isBlank();
        if (!hasContent && !hasFile) return;
        if (content == null || content.isBlank()) content = fileName != null ? fileName : typeStr.toLowerCase();

        ChatMessage.MessageType msgType;
        try { msgType = ChatMessage.MessageType.valueOf(typeStr.toUpperCase()); }
        catch (Exception e) { msgType = ChatMessage.MessageType.TEXT; }

        ChatMessageDTO saved = chatService.saveMessage(senderId, getUserName(session),
            getUserRole(session), recipientId, content, msgType, fileName, fileUrl);

        WsEnvelope outEnv = new WsEnvelope("MESSAGE", saved);
        sendToUser(recipientId, outEnv);
        sendToSession(session, outEnv);
    }

    @SuppressWarnings("unchecked")
    private void handleTyping(WebSocketSession session, String senderId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;
        String recipientId = (String) payload.get("recipientId");
        Boolean typing = (Boolean) payload.getOrDefault("typing", false);
        if (recipientId == null) return;
        sendToUser(recipientId, new WsEnvelope("TYPING", Map.of(
            "senderId", senderId, "senderName", getUserName(session),
            "recipientId", recipientId, "typing", typing)));
    }

    @SuppressWarnings("unchecked")
    private void handleRead(String recipientId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;
        String senderId = (String) payload.get("senderId");
        if (senderId == null) return;
        chatService.markRead(senderId, recipientId);
        sendToUser(senderId, new WsEnvelope("READ", Map.of("senderId", senderId, "recipientId", recipientId)));
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

    @SuppressWarnings("unchecked")
    private void handleSignaling(String fromUserId, WsEnvelope envelope) {
        Map<String, Object> payload = (Map<String, Object>) envelope.getPayload();
        if (payload == null) return;
        String toId     = (String) payload.get("toId");
        String callType = (String) payload.getOrDefault("callType", "AUDIO");
        String fromName = (String) payload.getOrDefault("fromName", "");
        if (toId == null) return;

        switch (envelope.getType()) {
            case "CALL_OFFER" -> {
                if (!presenceService.isOnline(toId)) {
                    // Destinataire hors ligne → appel manqué pour le destinataire
                    // callerId = fromUserId (l'appelant), recipientId = toId (le destinataire hors ligne)
                    try {
                        ChatMessageDTO missed = chatService.saveMissedCall(fromUserId, fromName, toId, callType);
                        // Notifier l'appelant que le destinataire est hors ligne
                        sendToUser(fromUserId, new WsEnvelope("CALL_REJECT", Map.of(
                            "type", "CALL_REJECT", "callId", payload.getOrDefault("callId", ""),
                            "callType", callType, "fromId", toId, "fromName", "", "toId", fromUserId, "reason", "OFFLINE")));
                        // Afficher le message "appel manqué" chez l'appelant
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                    } catch (Exception e) { log.error("[Chat WS] Failed to save missed call: {}", e.getMessage()); }
                } else {
                    sendToUser(toId, envelope);
                }
            }
            case "CALL_END" -> {
                // fromUserId = celui qui raccroche, toId = l'autre participant
                sendToUser(toId, envelope);
                Boolean wasAnswered = (Boolean) payload.getOrDefault("wasAnswered", false);
                if (Boolean.TRUE.equals(wasAnswered)) {
                    // Appel terminé normalement → sauvegarder avec durée
                    int durationSec = 0;
                    Object durObj = payload.get("durationSec");
                    if (durObj instanceof Number n) durationSec = n.intValue();
                    try {
                        ChatMessageDTO ended = chatService.saveCallEnded(fromUserId, fromName, toId, callType, durationSec);
                        sendToUser(toId,      new WsEnvelope("MESSAGE", ended));
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", ended));
                    } catch (Exception e) { log.error("[Chat WS] Failed to save call-ended: {}", e.getMessage()); }
                } else {
                    // Appel non répondu → appel manqué pour le destinataire (toId)
                    // callerId = fromUserId (l'appelant qui a raccroché), recipientId = toId
                    try {
                        ChatMessageDTO missed = chatService.saveMissedCall(fromUserId, fromName, toId, callType);
                        sendToUser(toId,      new WsEnvelope("MESSAGE", missed));
                        sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                    } catch (Exception e) { log.error("[Chat WS] Failed to save missed call: {}", e.getMessage()); }
                }
            }
            case "CALL_REJECT" -> {
                // fromUserId = le destinataire qui refuse, toId = l'appelant original
                // → appel manqué pour l'appelant (toId)
                sendToUser(toId, envelope);
                try {
                    // callerId = toId (l'appelant), recipientId = fromUserId (celui qui a refusé)
                    ChatMessageDTO missed = chatService.saveMissedCall(toId, fromName, fromUserId, callType);
                    sendToUser(toId,      new WsEnvelope("MESSAGE", missed));
                    sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                } catch (Exception e) { log.error("[Chat WS] Failed to save missed call on reject: {}", e.getMessage()); }
            }
            case "CALL_BUSY" -> {
                // fromUserId = le destinataire occupé, toId = l'appelant
                sendToUser(toId, envelope);
                try {
                    ChatMessageDTO missed = chatService.saveMissedCall(toId, fromName, fromUserId, callType);
                    sendToUser(toId,      new WsEnvelope("MESSAGE", missed));
                    sendToUser(fromUserId, new WsEnvelope("MESSAGE", missed));
                } catch (Exception e) { log.error("[Chat WS] Failed to save missed call on busy: {}", e.getMessage()); }
            }
            default -> sendToUser(toId, envelope);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessageAction(WebSocketSession session, String userId, WsEnvelope env) {
        Map<String, Object> payload = (Map<String, Object>) env.getPayload();
        if (payload == null) return;
        String action    = env.getType(); // MSG_EDIT, MSG_DELETE, MSG_FORWARD
        Object idObj     = payload.get("messageId");
        if (idObj == null) return;
        long messageId;
        try { messageId = Long.parseLong(idObj.toString()); } catch (Exception e) { return; }

        switch (action) {
            case "MSG_EDIT" -> {
                String newContent = (String) payload.get("content");
                if (newContent == null || newContent.isBlank()) return;
                try {
                    ChatMessageDTO updated = chatService.editMessage(messageId, userId, newContent);
                    String partnerId = updated.getRecipientId().equals(userId)
                            ? updated.getSenderId() : updated.getRecipientId();
                    WsEnvelope out = new WsEnvelope("MSG_EDIT", updated);
                    sendToUser(userId,     out);
                    sendToUser(partnerId,  out);
                } catch (Exception e) { log.error("[Chat WS] MSG_EDIT failed: {}", e.getMessage()); }
            }
            case "MSG_DELETE" -> {
                try {
                    ChatMessageDTO deleted = chatService.deleteMessage(messageId, userId);
                    String partnerId = deleted.getRecipientId().equals(userId)
                            ? deleted.getSenderId() : deleted.getRecipientId();
                    WsEnvelope out = new WsEnvelope("MSG_DELETE", Map.of("messageId", messageId));
                    sendToUser(userId,    out);
                    sendToUser(partnerId, out);
                } catch (Exception e) { log.error("[Chat WS] MSG_DELETE failed: {}", e.getMessage()); }
            }
            case "MSG_FORWARD" -> {
                String targetId = (String) payload.get("targetId");
                if (targetId == null) return;
                try {
                    ChatMessageDTO forwarded = chatService.forwardMessage(messageId, userId,
                            getUserName(session), getUserRole(session), targetId);
                    WsEnvelope out = new WsEnvelope("MESSAGE", forwarded);
                    sendToUser(targetId, out);
                    sendToSession(session, out);
                } catch (Exception e) { log.error("[Chat WS] MSG_FORWARD failed: {}", e.getMessage()); }
            }
        }
    }

    private void broadcastPresence(String userId, String username, boolean online) {
        WsEnvelope env = new WsEnvelope("PRESENCE", Map.of("userId", userId, "username", username, "online", online));
        presenceService.getOnlineUserIds().forEach(uid -> sendToUser(uid, env));
    }

    private void sendToUser(String userId, WsEnvelope envelope) {
        presenceService.getSessions(userId).forEach(s -> sendToSession(s, envelope));
    }

    private void sendToSession(WebSocketSession session, WsEnvelope envelope) {
        if (!session.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(envelope);
            synchronized (session) { session.sendMessage(new TextMessage(json)); }
        } catch (IOException e) {
            log.warn("[Chat WS] Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String getUserId(WebSocketSession session)   { return (String) session.getAttributes().get("userId"); }
    private String getUserName(WebSocketSession session) { Object v = session.getAttributes().get("username"); return v != null ? (String) v : "Unknown"; }
    private String getUserRole(WebSocketSession session) { Object v = session.getAttributes().get("role"); return v != null ? (String) v : "USER"; }
    private void closeQuietly(WebSocketSession session)  { try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {} }
}
