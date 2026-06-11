package com.wifak.chatservice.service;

import com.wifak.chatservice.dto.ChatContactDTO;
import com.wifak.chatservice.dto.ChatMessageDTO;
import com.wifak.chatservice.dto.UserDTO;
import com.wifak.chatservice.entities.ChatMessage;
import com.wifak.chatservice.feign.AuthentificationFeignClient;
import com.wifak.chatservice.repositories.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService — Tests unitaires")
class ChatServiceTest {

    @Mock
    private ChatMessageRepository msgRepo;

    @Mock
    private PresenceService presenceService;

    @Mock
    private AuthentificationFeignClient authClient;

    @InjectMocks
    private ChatService chatService;

    private ChatMessage buildMessage(Long id, String senderId, String recipientId,
                                     String content, ChatMessage.MessageType type) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setSenderId(senderId);
        m.setSenderName("Agent Test");
        m.setSenderRole("ROLE_AGENT");
        m.setRecipientId(recipientId);
        m.setRecipientName("Manager Test");
        m.setContent(content);
        m.setType(type);
        m.setSentAt(LocalDateTime.now());
        m.setIsRead(false);
        m.setIsDeleted(false);
        m.setIsForwarded(false);
        return m;
    }

    private UserDTO buildUser(String id, String username, String firstName, String lastName) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setUsername(username);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEnabled(true);
        u.setRoles(List.of("ROLE_AGENT"));
        return u;
    }

    // ══════════════════════════════════════════════════════════════
    // saveMessage
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveMessage — cas normal → sauvegarde et retourne DTO")
    void saveMessage_normal_ok() {
        // Arrange
        when(authClient.getUserById("recipient-1")).thenReturn(buildUser("recipient-1", "manager1", "Marie", "Dupont"));
        ChatMessage saved = buildMessage(1L, "sender-1", "recipient-1", "Bonjour", ChatMessage.MessageType.TEXT);
        when(msgRepo.save(any(ChatMessage.class))).thenReturn(saved);

        // Act
        ChatMessageDTO result = chatService.saveMessage(
            "sender-1", "Agent Test", "ROLE_AGENT",
            "recipient-1", "Bonjour",
            ChatMessage.MessageType.TEXT, null, null
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Bonjour");
        assertThat(result.getSenderId()).isEqualTo("sender-1");
        assertThat(result.getType()).isEqualTo("TEXT");
        verify(msgRepo).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("saveMessage — content null → sauvegardé avec contenu vide")
    void saveMessage_contentNull_savedWithEmpty() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "user", "U", "U"));
        ChatMessage saved = buildMessage(1L, "s", "r", "", ChatMessage.MessageType.TEXT);
        when(msgRepo.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageDTO result = chatService.saveMessage("s", "Sender", "ROLE_AGENT", "r", null,
            ChatMessage.MessageType.TEXT, null, null);

        assertThat(result.getContent()).isEqualTo("");
        verify(msgRepo).save(argThat(m -> m.getContent().isEmpty()));
    }

    @Test
    @DisplayName("saveMessage — type FILE avec fileName et fileUrl → sauvegardé correctement")
    void saveMessage_fileType_savedWithFileInfo() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "user", "U", "U"));
        ChatMessage saved = buildMessage(1L, "s", "r", "document.pdf", ChatMessage.MessageType.FILE);
        saved.setFileName("document.pdf");
        saved.setFileUrl("http://localhost:8083/api/chat/files/uuid.pdf");
        when(msgRepo.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageDTO result = chatService.saveMessage("s", "Sender", "ROLE_AGENT", "r",
            "document.pdf", ChatMessage.MessageType.FILE,
            "document.pdf", "http://localhost:8083/api/chat/files/uuid.pdf");

        assertThat(result.getType()).isEqualTo("FILE");
        assertThat(result.getFileName()).isEqualTo("document.pdf");
        verify(msgRepo).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("saveMessage — authClient échoue → sauvegarde quand même avec userId comme nom")
    void saveMessage_authClientFails_stillSaves() {
        when(authClient.getUserById(anyString())).thenThrow(new RuntimeException("Service indisponible"));
        ChatMessage saved = buildMessage(1L, "s", "r", "Bonjour", ChatMessage.MessageType.TEXT);
        when(msgRepo.save(any(ChatMessage.class))).thenReturn(saved);

        // Ne doit pas lever d'exception
        ChatMessageDTO result = chatService.saveMessage("s", "Sender", "ROLE_AGENT", "r",
            "Bonjour", ChatMessage.MessageType.TEXT, null, null);

        assertThat(result).isNotNull();
        verify(msgRepo).save(any(ChatMessage.class));
    }

    // ══════════════════════════════════════════════════════════════
    // editMessage
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("editMessage — propriétaire du message → modification réussie")
    void editMessage_proprietaire_ok() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "Ancien contenu", ChatMessage.MessageType.TEXT);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageDTO result = chatService.editMessage(1L, "sender-1", "Nouveau contenu");

        assertThat(result.getContent()).isEqualTo("Nouveau contenu");
        assertThat(result.getEditedAt()).isNotNull();
        verify(msgRepo).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("editMessage — message inexistant → IllegalArgumentException")
    void editMessage_messageInexistant_throwsIllegalArgument() {
        when(msgRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.editMessage(99L, "sender-1", "Contenu"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Message not found: 99");
    }

    @Test
    @DisplayName("editMessage — autre utilisateur → SecurityException")
    void editMessage_autreUtilisateur_throwsSecurityException() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "Contenu", ChatMessage.MessageType.TEXT);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> chatService.editMessage(1L, "autre-user", "Nouveau contenu"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Not allowed to edit");
    }

    @Test
    @DisplayName("editMessage — message supprimé → IllegalStateException")
    void editMessage_messageSuprime_throwsIllegalState() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "", ChatMessage.MessageType.TEXT);
        msg.setIsDeleted(true);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> chatService.editMessage(1L, "sender-1", "Nouveau contenu"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot edit a deleted message");
    }

    // ══════════════════════════════════════════════════════════════
    // deleteMessage
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteMessage — propriétaire → soft delete réussi")
    void deleteMessage_proprietaire_softDeleteOk() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "Contenu", ChatMessage.MessageType.TEXT);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageDTO result = chatService.deleteMessage(1L, "sender-1");

        assertThat(result.getIsDeleted()).isTrue();
        assertThat(result.getContent()).isEqualTo("");
        assertThat(result.getFileName()).isNull();
        assertThat(result.getFileUrl()).isNull();
        verify(msgRepo).save(argThat(m ->
            Boolean.TRUE.equals(m.getIsDeleted()) &&
            m.getContent().isEmpty()
        ));
    }

    @Test
    @DisplayName("deleteMessage — autre utilisateur → SecurityException")
    void deleteMessage_autreUtilisateur_throwsSecurityException() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "Contenu", ChatMessage.MessageType.TEXT);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> chatService.deleteMessage(1L, "autre-user"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Not allowed to delete");
    }

    @Test
    @DisplayName("deleteMessage — message inexistant → IllegalArgumentException")
    void deleteMessage_inexistant_throwsIllegalArgument() {
        when(msgRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.deleteMessage(99L, "sender-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Message not found: 99");
    }

    // ══════════════════════════════════════════════════════════════
    // forwardMessage
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("forwardMessage — message valide → transféré avec isForwarded=true")
    void forwardMessage_valide_ok() {
        ChatMessage original = buildMessage(1L, "sender-1", "recipient-1",
            "Message original", ChatMessage.MessageType.TEXT);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(original));
        when(authClient.getUserById("target-1")).thenReturn(buildUser("target-1", "manager2", "Marc", "Durand"));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(2L);
            return m;
        });

        ChatMessageDTO result = chatService.forwardMessage(1L, "forwarder-1",
            "Agent Forward", "ROLE_AGENT", "target-1");

        assertThat(result.getIsForwarded()).isTrue();
        assertThat(result.getContent()).isEqualTo("Message original");
        assertThat(result.getSenderId()).isEqualTo("forwarder-1");
        assertThat(result.getRecipientId()).isEqualTo("target-1");
        verify(msgRepo).save(argThat(m -> Boolean.TRUE.equals(m.getIsForwarded())));
    }

    @Test
    @DisplayName("forwardMessage — message supprimé → IllegalStateException")
    void forwardMessage_messageSuprime_throwsIllegalState() {
        ChatMessage msg = buildMessage(1L, "sender-1", "recipient-1", "", ChatMessage.MessageType.TEXT);
        msg.setIsDeleted(true);
        when(msgRepo.findById(1L)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() ->
            chatService.forwardMessage(1L, "forwarder-1", "Agent", "ROLE_AGENT", "target-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot forward a deleted message");
    }

    @Test
    @DisplayName("forwardMessage — message inexistant → IllegalArgumentException")
    void forwardMessage_inexistant_throwsIllegalArgument() {
        when(msgRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            chatService.forwardMessage(99L, "f", "name", "role", "target"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ══════════════════════════════════════════════════════════════
    // saveMissedCall
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveMissedCall — appel audio → message MISSED_CALL sauvegardé")
    void saveMissedCall_audio_ok() {
        when(authClient.getUserById("recipient-1")).thenReturn(buildUser("recipient-1", "manager", "M", "D"));
        ChatMessage saved = buildMessage(1L, "caller-1", "recipient-1",
            "Appel audio manqué", ChatMessage.MessageType.MISSED_CALL);
        when(msgRepo.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageDTO result = chatService.saveMissedCall("caller-1", "Agent Test", "recipient-1", "AUDIO");

        assertThat(result.getType()).isEqualTo("MISSED_CALL");
        assertThat(result.getContent()).containsIgnoringCase("appel");
        verify(msgRepo).save(argThat(m -> m.getType() == ChatMessage.MessageType.MISSED_CALL));
    }

    @Test
    @DisplayName("saveMissedCall — appel vidéo → contenu contient 'vidéo'")
    void saveMissedCall_video_contentContainsVideo() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "u", "U", "U"));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        ChatMessageDTO result = chatService.saveMissedCall("caller", "Caller", "recipient", "VIDEO");

        assertThat(result.getType()).isEqualTo("MISSED_CALL");
        verify(msgRepo).save(argThat(m -> m.getContent().contains("vidéo") || m.getContent().contains("Vid")));
    }

    // ══════════════════════════════════════════════════════════════
    // saveCallEnded
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveCallEnded — durée en secondes < 60 → affichage '30s'")
    void saveCallEnded_durationSec_ok() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "u", "U", "U"));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        ChatMessageDTO result = chatService.saveCallEnded("caller", "Caller", "recipient", "AUDIO", 30);

        assertThat(result.getType()).isEqualTo("CALL_ENDED");
        verify(msgRepo).save(argThat(m ->
            m.getType() == ChatMessage.MessageType.CALL_ENDED &&
            m.getContent().contains("30s")
        ));
    }

    @Test
    @DisplayName("saveCallEnded — durée 90 secondes → affichage '1 min 30s'")
    void saveCallEnded_durationMin_ok() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "u", "U", "U"));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        chatService.saveCallEnded("caller", "Caller", "recipient", "AUDIO", 90);

        verify(msgRepo).save(argThat(m -> m.getContent().contains("1 min 30s")));
    }

    @Test
    @DisplayName("saveCallEnded — durée 120 secondes → affichage '2 min'")
    void saveCallEnded_duration2min_ok() {
        when(authClient.getUserById(anyString())).thenReturn(buildUser("r", "u", "U", "U"));
        when(msgRepo.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        chatService.saveCallEnded("caller", "Caller", "recipient", "AUDIO", 120);

        verify(msgRepo).save(argThat(m -> m.getContent().contains("2 min")));
    }

    // ══════════════════════════════════════════════════════════════
    // getConversation
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getConversation — historique existant → retourne liste triée")
    void getConversation_historique_ok() {
        List<ChatMessage> messages = List.of(
            buildMessage(1L, "user-1", "user-2", "Bonjour", ChatMessage.MessageType.TEXT),
            buildMessage(2L, "user-2", "user-1", "Salut", ChatMessage.MessageType.TEXT),
            buildMessage(3L, "user-1", "user-2", "Comment ça va ?", ChatMessage.MessageType.TEXT)
        );
        when(msgRepo.findConversation("user-1", "user-2")).thenReturn(messages);

        List<ChatMessageDTO> result = chatService.getConversation("user-1", "user-2");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getContent()).isEqualTo("Bonjour");
        assertThat(result.get(2).getContent()).isEqualTo("Comment ça va ?");
    }

    @Test
    @DisplayName("getConversation — aucun message → liste vide")
    void getConversation_vide_retourneListeVide() {
        when(msgRepo.findConversation("user-1", "user-2")).thenReturn(List.of());

        List<ChatMessageDTO> result = chatService.getConversation("user-1", "user-2");

        assertThat(result).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // markRead
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("markRead — appelle repository correctement")
    void markRead_appelleRepository() {
        when(msgRepo.markAllRead("sender-1", "recipient-1")).thenReturn(3);

        chatService.markRead("sender-1", "recipient-1");

        verify(msgRepo).markAllRead("sender-1", "recipient-1");
    }

    // ══════════════════════════════════════════════════════════════
    // getContacts
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getContacts — ROLE_AGENT → retourne contacts sans soi-même")
    void getContacts_agent_returnsSansLuiMeme() {
        UserDTO agent1 = buildUser("user-1", "agent1", "Alice", "Martin");
        UserDTO agent2 = buildUser("user-2", "agent2", "Bob", "Dupont");
        UserDTO manager = buildUser("user-3", "manager1", "Carol", "Martin");
        manager.setRoles(List.of("ROLE_MANAGER"));

        when(authClient.getUsersByRole("ROLE_AGENT")).thenReturn(List.of(agent1, agent2));
        when(authClient.getUsersByRole("ROLE_MANAGER")).thenReturn(List.of(manager));
        when(authClient.getUsersByRole("ROLE_ADMIN")).thenReturn(List.of());
        when(presenceService.isOnline(anyString())).thenReturn(false);
        when(msgRepo.countUnread(anyString(), anyString())).thenReturn(0L);
        when(msgRepo.findLatestMessagePage(anyString(), anyString(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        List<ChatContactDTO> contacts = chatService.getContacts("user-1", Set.of("ROLE_AGENT"));

        // user-1 ne doit pas être dans sa propre liste
        assertThat(contacts).noneMatch(c -> c.getId().equals("user-1"));
        assertThat(contacts).hasSize(2); // agent2 + manager1
    }

    @Test
    @DisplayName("getContacts — aucun rôle chat → liste vide")
    void getContacts_aucunRole_listeVide() {
        List<ChatContactDTO> contacts = chatService.getContacts("user-1", Set.of("ROLE_AUDITOR"));

        assertThat(contacts).isEmpty();
        verifyNoInteractions(authClient);
    }

    @Test
    @DisplayName("getContacts — utilisateur désactivé → exclu de la liste")
    void getContacts_utilisateurDisable_exclu() {
        UserDTO active = buildUser("user-2", "agent2", "Bob", "D");
        UserDTO disabled = buildUser("user-3", "agent3", "Eve", "D");
        disabled.setEnabled(false);

        when(authClient.getUsersByRole("ROLE_AGENT")).thenReturn(List.of(active, disabled));
        when(authClient.getUsersByRole("ROLE_MANAGER")).thenReturn(List.of());
        when(authClient.getUsersByRole("ROLE_ADMIN")).thenReturn(List.of());
        when(presenceService.isOnline(anyString())).thenReturn(false);
        when(msgRepo.countUnread(anyString(), anyString())).thenReturn(0L);
        when(msgRepo.findLatestMessagePage(anyString(), anyString(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        List<ChatContactDTO> contacts = chatService.getContacts("user-1", Set.of("ROLE_AGENT"));

        assertThat(contacts).noneMatch(c -> c.getId().equals("user-3"));
        assertThat(contacts).anyMatch(c -> c.getId().equals("user-2"));
    }

    @Test
    @DisplayName("getContacts — contact en ligne → online=true")
    void getContacts_utilisateurEnLigne_onlineTrue() {
        UserDTO user2 = buildUser("user-2", "agent2", "Bob", "D");
        when(authClient.getUsersByRole("ROLE_AGENT")).thenReturn(List.of(user2));
        when(authClient.getUsersByRole("ROLE_MANAGER")).thenReturn(List.of());
        when(authClient.getUsersByRole("ROLE_ADMIN")).thenReturn(List.of());
        when(presenceService.isOnline("user-2")).thenReturn(true);
        when(msgRepo.countUnread(anyString(), anyString())).thenReturn(0L);
        when(msgRepo.findLatestMessagePage(anyString(), anyString(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        List<ChatContactDTO> contacts = chatService.getContacts("user-1", Set.of("ROLE_AGENT"));

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).isOnline()).isTrue();
    }

    @Test
    @DisplayName("getContacts — contact avec messages non lus → unread > 0")
    void getContacts_messagesNonLus_unreadPositif() {
        UserDTO user2 = buildUser("user-2", "agent2", "Bob", "D");
        when(authClient.getUsersByRole("ROLE_AGENT")).thenReturn(List.of(user2));
        when(authClient.getUsersByRole("ROLE_MANAGER")).thenReturn(List.of());
        when(authClient.getUsersByRole("ROLE_ADMIN")).thenReturn(List.of());
        when(presenceService.isOnline(anyString())).thenReturn(false);
        when(msgRepo.countUnread("user-2", "user-1")).thenReturn(5L);
        when(msgRepo.findLatestMessagePage(anyString(), anyString(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        List<ChatContactDTO> contacts = chatService.getContacts("user-1", Set.of("ROLE_AGENT"));

        assertThat(contacts.get(0).getUnread()).isEqualTo(5L);
    }
}
