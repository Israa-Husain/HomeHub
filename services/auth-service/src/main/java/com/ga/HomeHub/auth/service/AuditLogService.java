package com.ga.HomeHub.auth.service;

import com.ga.HomeHub.auth.model.AuditLog;
import com.ga.HomeHub.auth.model.User;
import com.ga.HomeHub.auth.repository.AuditLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repository;

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
