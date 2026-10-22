
package com.ga.HomeHub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
public class ServiceReview extends BaseEntity{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
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
