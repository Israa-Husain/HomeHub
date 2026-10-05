package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
public class Availability extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private ProviderProfile provider;

    @Column(nullable=false)
    private LocalDate date;

    @Column(nullable=false)
    private LocalTime startTime;

    @Column(nullable=false)
    private LocalTime endTime;

    @Column(nullable=false)
    private boolean isAvailable = true;

    //check if the requested booking is within the provider's available time.
    public boolean contains(LocalTime start,LocalTime end){
        return isAvailable&&!start.isBefore(startTime)&&!end.isAfter(endTime);
    }

}
