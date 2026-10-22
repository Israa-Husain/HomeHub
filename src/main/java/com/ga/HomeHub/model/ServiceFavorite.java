
package com.ga.HomeHub.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Data
@Table(name="service_favorite",uniqueConstraints=@UniqueConstraint(columnNames={"homeowner_id","service_id"}))
public class ServiceFavorite extends BaseEntity{
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="homeowner_id",nullable=false)
    private User homeowner;

    @ManyToOne(optional=false)
    @JoinColumn(name="service_id",nullable=false)
    private ServiceOffering service;
}
