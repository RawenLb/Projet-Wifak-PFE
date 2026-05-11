package com.wifak.chatservice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) sessions.remove(userId);
        }
    }

    public boolean isOnline(String userId) {
        Set<WebSocketSession> s = sessions.get(userId);
        return s != null && !s.isEmpty();
    }

    public Set<WebSocketSession> getSessions(String userId) {
        return sessions.getOrDefault(userId, Set.of());
    }

    public Set<String> getOnlineUserIds() {
        return sessions.keySet();
    }
}
