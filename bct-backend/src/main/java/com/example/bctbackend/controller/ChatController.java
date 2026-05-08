package com.example.bctbackend.controller;

import com.example.bctbackend.dto.ChatContactDTO;
import com.example.bctbackend.dto.ChatMessageDTO;
import com.example.bctbackend.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ── GET /api/chat/contacts ────────────────────────────────────
    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('ROLE_AGENT','ROLE_MANAGER')")
    public ResponseEntity<List<ChatContactDTO>> getContacts(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        String userId = jwt.getSubject();
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return ResponseEntity.ok(chatService.getContacts(userId, roles));
    }

    // ── GET /api/chat/history/{partnerId} ─────────────────────────
    @GetMapping("/history/{partnerId}")
    @PreAuthorize("hasAnyRole('ROLE_AGENT','ROLE_MANAGER')")
    public ResponseEntity<List<ChatMessageDTO>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partnerId
    ) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(chatService.getConversation(userId, partnerId));
    }

    // ── POST /api/chat/read/{senderId} ────────────────────────────
    @PostMapping("/read/{senderId}")
    @PreAuthorize("hasAnyRole('ROLE_AGENT','ROLE_MANAGER')")
    public ResponseEntity<Map<String, String>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String senderId
    ) {
        String recipientId = jwt.getSubject();
        chatService.markRead(senderId, recipientId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
