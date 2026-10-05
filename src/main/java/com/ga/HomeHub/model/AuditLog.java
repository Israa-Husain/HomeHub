package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @Column(nullable=false)
    private String action;
    private String entityType;
    private Long entityId;

    @Column
    private String description;

    @Column(nullable=false)
    private LocalDateTime createdAt = LocalDateTime.now();

}
