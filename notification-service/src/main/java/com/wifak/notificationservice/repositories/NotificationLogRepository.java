package com.wifak.notificationservice.repositories;

import com.wifak.notificationservice.entities.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByDeclarationIdOrderByDateSentDesc(Long declarationId);

    List<NotificationLog> findByNotificationType(String notificationType);

    List<NotificationLog> findByStatut(String statut);
}