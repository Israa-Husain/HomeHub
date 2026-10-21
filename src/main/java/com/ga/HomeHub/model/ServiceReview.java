package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Entity
@Data
public class ServiceReview extends BaseEntity{
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional=false)
    @JoinColumn(unique=true)
    private Booking booking;

    @ManyToOne(optional=false)
    private User homeowner;

    @Column(nullable=false)
    private int rating;

    @Column(length=3000)
    private String comment;
}
