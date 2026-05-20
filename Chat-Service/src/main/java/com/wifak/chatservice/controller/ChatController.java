package com.wifak.chatservice.controller;

import com.wifak.chatservice.dto.ChatContactDTO;
import com.wifak.chatservice.dto.ChatMessageDTO;
import com.wifak.chatservice.service.ChatService;
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

    @GetMapping("/contacts")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<List<ChatContactDTO>> getContacts(
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        String userId = jwt.getSubject();
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return ResponseEntity.ok(chatService.getContacts(userId, roles));
    }

    @GetMapping("/history/{partnerId}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<List<ChatMessageDTO>> getHistory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String partnerId) {
        return ResponseEntity.ok(chatService.getConversation(jwt.getSubject(), partnerId));
    }

    @PostMapping("/read/{senderId}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> markRead(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String senderId) {
        chatService.markRead(senderId, jwt.getSubject());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/messages/{messageId}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<ChatMessageDTO> editMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long messageId,
            @RequestBody Map<String, String> body) {
        String newContent = body.get("content");
        if (newContent == null || newContent.isBlank())
            return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(chatService.editMessage(messageId, jwt.getSubject(), newContent));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/messages/{messageId}")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<ChatMessageDTO> deleteMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long messageId) {
        try {
            return ResponseEntity.ok(chatService.deleteMessage(messageId, jwt.getSubject()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/messages/{messageId}/forward")
    @PreAuthorize("hasAnyAuthority('ROLE_AGENT','ROLE_MANAGER','ROLE_ADMIN')")
    public ResponseEntity<ChatMessageDTO> forwardMessage(
            @AuthenticationPrincipal Jwt jwt, Authentication authentication,
            @PathVariable Long messageId,
            @RequestBody Map<String, String> body) {
        String targetId = body.get("targetId");
        if (targetId == null || targetId.isBlank())
            return ResponseEntity.badRequest().build();
        String name = jwt.getClaimAsString("preferred_username");
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).findFirst().orElse("USER");
        try {
            return ResponseEntity.ok(chatService.forwardMessage(messageId, jwt.getSubject(), name, role, targetId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
