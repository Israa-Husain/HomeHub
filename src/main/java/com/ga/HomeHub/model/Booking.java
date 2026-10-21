package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Data
@Table(name = "bookings")
public class Booking extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User customer;

    @ManyToOne(optional = false)
    private Home home;

    @ManyToOne(optional = false)
    private ServiceOffering service;

    @Column(nullable=false)
    private LocalDate bookingDate;

    @Column(nullable=false)
    private LocalTime startTime;

    @Column(nullable=false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING) private BookingType bookingType=BookingType.NORMAL;
    private Double finalPrice;

    private LocalDateTime responseDeadline;

    private LocalDateTime reportDeadline;

    private boolean lateCancellation=false;

    private boolean reminder24Sent=false;

    private boolean reminder1Sent=false;

    private boolean reportOverdueNotified=false;

    @Column
    private String note;

}
