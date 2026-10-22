package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Entity
@Data
@Table(name="service_favorite", uniqueConstraints=@UniqueConstraint(columnNames={"homeowner_id","service_id"}))
public class ServiceFavorite extends BaseEntity{
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="homeowner_id",nullable=false)
    private User homeowner;

    @ManyToOne(optional=false)
    @JoinColumn(name="service_id",nullable=false)
    private ServiceOffering service;
}
