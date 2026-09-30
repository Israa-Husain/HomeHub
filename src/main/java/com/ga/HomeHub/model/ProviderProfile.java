package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.ProviderStatus;
import jakarta.persistence.*;

@Entity
public class ProviderProfile extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column
    private String businessName;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    private ProviderStatus providerStatus = ProviderStatus.PENDING;


    //GETTERS & SETTERS
    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProviderStatus getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(ProviderStatus providerStatus) {
        this.providerStatus = providerStatus;
    }
}
