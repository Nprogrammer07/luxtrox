package com.luxtrox.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxtrox.backend.entity.AuditLog;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Toda operacion financiera debe registrar quien, cuando, que entidad,
 * valor anterior y valor nuevo (ver docs/domain-model.md principio 6).
 * Este servicio centraliza ese registro para no repetir el armado de
 * JSON en cada servicio de negocio.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(User actor, String entityType, UUID entityId, String action,
                        Object oldValue, Object newValue) {
        auditLogRepository.save(new AuditLog(
                actor,
                entityType,
                entityId,
                action,
                toJson(oldValue),
                toJson(newValue)
        ));
    }

    /** Para acciones disparadas por el sistema (jobs, cron), sin un usuario humano detras. */
    public void recordSystemAction(String entityType, UUID entityId, String action,
                                    Object oldValue, Object newValue) {
        record(null, entityType, entityId, action, oldValue, newValue);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"no se pudo serializar: " + e.getMessage() + "\"}";
        }
    }
}
