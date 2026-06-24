package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
