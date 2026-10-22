package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ServiceReport extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Booking booking;

    @ManyToOne(optional = false)
    private ProviderProfile provider;

    @Column(nullable = false, length = 3000)
    private String workPerformed;

    @Column(length = 3000)
    private String materialsUsed;

    @Column(length = 3000)
    private String recommendations;
}
