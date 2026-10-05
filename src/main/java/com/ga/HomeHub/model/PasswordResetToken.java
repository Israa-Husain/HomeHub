package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(nullable=false, unique = true)
    private String token;

    @Column(nullable=false)
    private LocalDateTime expiredAt;
    private LocalDateTime usedAt;

    @Column(nullable=false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isExpired(){
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public void markUsed(){
        usedAt = LocalDateTime.now();
    }
}
