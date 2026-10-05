package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Home extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User owner;

    @Column(nullable=false)
    private String name;

    @Column(nullable=false)
    private String city;

    @Column(nullable=false)
    private String address;

}
