package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.RescheduleStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.LocalTime;

public class BookingRescheduleRequest extends BaseEntity{
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Booking booking;

    @ManyToOne(optional=false)
    private User requester;

    @Column(nullable=false)
    private LocalDate requestedDate;

    @Column(nullable=false)
    private LocalTime requestedStartTime;

    @Enumerated(EnumType.STRING)
    private RescheduleStatus status=RescheduleStatus.PENDING;
}
