package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.CategoryStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Category extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private CategoryStatus status = CategoryStatus.ACTIVE;

}
