package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "services")
public class ServiceOffering extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private ProviderProfile provider;

    @ManyToOne(optional = false)
    private Category category;

    @Column(nullable=false)
    private String name;

    @Column
    private String description;

    @Column(nullable=false)
    private Integer durationMinutes;

    @Column(nullable=false)
    private Double price;

    @Enumerated(EnumType.STRING)
    private ServiceStatus status = ServiceStatus.ACTIVE;

}
