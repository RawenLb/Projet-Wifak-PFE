package com.example.bctbackend.repositories;

import com.example.bctbackend.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Full conversation between two users (both directions), ordered by time.
     */
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

    /**
     * Last N messages of a conversation.
     */
    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userId AND m.recipientId = :partnerId)
           OR (m.senderId = :partnerId AND m.recipientId = :userId)
        ORDER BY m.sentAt DESC
        LIMIT :limit
        """)
    List<ChatMessage> findLastN(
        @Param("userId")    String userId,
        @Param("partnerId") String partnerId,
        @Param("limit")     int limit
    );

    /**
     * Count unread messages sent by partnerId to userId.
     */
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

    /**
     * Mark all messages from senderId to recipientId as read.
     */
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

    /**
     * Mark a single message as read.
     */
    @Modifying
    @Query("""
        UPDATE ChatMessage m
        SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP
        WHERE m.id = :id
        """)
    int markOneRead(@Param("id") Long id);

    /**
     * IDs of all users who have exchanged at least one message with userId.
     */
    @Query("""
        SELECT DISTINCT
            CASE WHEN m.senderId = :userId THEN m.recipientId ELSE m.senderId END
        FROM ChatMessage m
        WHERE m.senderId = :userId OR m.recipientId = :userId
        """)
    List<String> findContactIds(@Param("userId") String userId);

    /**
     * Latest message in a conversation (for contact list preview).
     */
    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userId AND m.recipientId = :partnerId)
           OR (m.senderId = :partnerId AND m.recipientId = :userId)
        ORDER BY m.sentAt DESC
        LIMIT 1
        """)
    java.util.Optional<ChatMessage> findLatestMessage(
        @Param("userId")    String userId,
        @Param("partnerId") String partnerId
    );
}
