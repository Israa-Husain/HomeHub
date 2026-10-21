package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Id;
@Entity
@Data
public class HomeownerAssessment extends BaseEntity{
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional=false)
    @JoinColumn(unique=true)
    private Booking booking;

    @ManyToOne(optional=false)
    private User homeowner;

    @ManyToOne(optional=false)
    private ProviderProfile author;

    private Integer rating;

    @Column(length=2000) private String comment;

}
