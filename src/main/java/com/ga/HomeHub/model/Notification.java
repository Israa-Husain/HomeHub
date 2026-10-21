package com.ga.HomeHub.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Id;
@Entity
@Data
public class Notification extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private User recipient;

    @Column(nullable=false,length=3000)
    private String message;

    @Column(nullable=false)
    private boolean isRead=false;

    private String eventType="booking";

}
