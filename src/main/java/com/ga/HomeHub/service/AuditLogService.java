package com.ga.HomeHub.service;

import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(User user, String action, String type, Long id, String description){
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setAction(action);
        auditLog.setEntityType(type);
        auditLog.setEntityId(id);
        auditLog.setDescription(description);

        repository.save(auditLog);
    }
}
