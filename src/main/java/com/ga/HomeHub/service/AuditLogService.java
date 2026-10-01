package com.ga.HomeHub.service;

import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.AuditLogRepository;
<<<<<<< HEAD
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

=======
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuditLogService {
    private final AuditLogRepository repository;

>>>>>>> feature/repositories
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
