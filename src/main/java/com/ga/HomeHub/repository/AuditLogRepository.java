package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    //Scope paging to the selected resource before any audit records leave the repository.
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(String entityType,Long entityId,Pageable pageable);
}
