package com.ga.HomeHub.booking.service;


import com.ga.HomeHub.booking.model.AuditLog;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.repository.AuditLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@AllArgsConstructor
@Transactional
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

    //BookingService checks participant access before requesting these existing audit records.
    @Transactional(readOnly=true)
    public Page<AuditLog> getBookingHistory(Long bookingId,Pageable pageable){
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc("Booking",bookingId,pageable);
    }
}