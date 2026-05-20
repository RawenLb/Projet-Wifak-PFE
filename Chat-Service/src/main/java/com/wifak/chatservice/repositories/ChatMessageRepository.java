package com.wifak.chatservice.repositories;

import com.wifak.chatservice.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userId AND m.recipientId = :partnerId)
           OR (m.senderId = :partnerId AND m.recipientId = :userId)
        ORDER BY m.sentAt ASC
        """)
    List<ChatMessage> findConversation(
        @Param("userId")    String userId,
        @Param("partnerId") String partnerId
    );

    @Query("""
        SELECT COUNT(m) FROM ChatMessage m
        WHERE m.senderId = :senderId
          AND m.recipientId = :recipientId
          AND m.isRead = false
        """)
    long countUnread(
        @Param("senderId")    String senderId,
        @Param("recipientId") String recipientId
    );

    @Modifying
    @Query("""
        UPDATE ChatMessage m
        SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP
        WHERE m.senderId = :senderId
          AND m.recipientId = :recipientId
          AND m.isRead = false
        """)
    int markAllRead(
        @Param("senderId")    String senderId,
        @Param("recipientId") String recipientId
    );

    @Modifying
    @Query("""
        UPDATE ChatMessage m
        SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP
        WHERE m.id = :id
        """)
    int markOneRead(@Param("id") Long id);

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userId AND m.recipientId = :partnerId)
           OR (m.senderId = :partnerId AND m.recipientId = :userId)
        ORDER BY m.sentAt DESC
        """)
    org.springframework.data.domain.Page<ChatMessage> findLatestMessagePage(
        @Param("userId")    String userId,
        @Param("partnerId") String partnerId,
        org.springframework.data.domain.Pageable pageable
    );
}
