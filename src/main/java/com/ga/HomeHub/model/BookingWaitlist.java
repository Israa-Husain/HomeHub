package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.WaitlistStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class BookingWaitlist extends BaseEntity{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private User homeowner;

    @ManyToOne(optional=false)
    private ServiceOffering service;

    @ManyToOne(optional=false)
    private Home home;

    @Column(nullable=false)
    private LocalDate requestedDate;

    @Column(nullable=false)
    private LocalTime requestedStartTime;

    @Enumerated(EnumType.STRING)
    private WaitlistStatus status=WaitlistStatus.WAITING;

    private LocalDateTime offerExpiresAt;

}
