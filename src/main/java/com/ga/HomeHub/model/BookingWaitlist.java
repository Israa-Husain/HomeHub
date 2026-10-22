
package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.WaitlistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
@Entity
@Data
public class BookingWaitlist extends BaseEntity {
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
