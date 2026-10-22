
package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.RescheduleStatus;
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
import java.time.LocalTime;

@Entity
@Data
public class BookingRescheduleRequest extends BaseEntity{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
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
