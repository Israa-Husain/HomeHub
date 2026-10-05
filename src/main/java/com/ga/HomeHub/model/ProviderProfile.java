package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.ProviderStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ProviderProfile extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable=false)
    private String businessName;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    private ProviderStatus providerStatus = ProviderStatus.PENDING;

}
