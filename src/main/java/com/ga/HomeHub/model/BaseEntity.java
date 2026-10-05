package com.ga.HomeHub.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseEntity {
    @Column
    protected LocalDateTime createdAt;

    @Column
    protected LocalDateTime updatedAt;

    @CreationTimestamp
    void created(){createdAt=updatedAt=LocalDateTime.now();}

    @UpdateTimestamp
    void updated(){updatedAt=LocalDateTime.now();}

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
