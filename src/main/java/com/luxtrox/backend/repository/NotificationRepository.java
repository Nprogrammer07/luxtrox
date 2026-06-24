package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Notification;
import com.luxtrox.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    List<Notification> findByUserAndIsReadFalse(User user);
}
