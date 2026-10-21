package com.ga.HomeHub.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import org.springframework.data.annotation.Id;

public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private User recipient;

    @Column(nullable=false,length=3000)
    private String message;

    @Column(nullable=false)
    private boolean isRead=false;

    private String eventType="booking";

}
