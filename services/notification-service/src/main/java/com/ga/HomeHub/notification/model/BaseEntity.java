package com.ga.HomeHub.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
@Data
public abstract class BaseEntity{
    @Column(name="created_at",nullable=false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name="updated_at",nullable=false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}