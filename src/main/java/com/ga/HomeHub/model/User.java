package com.ga.HomeHub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
public class User extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String firstName;

    @Column(nullable=false)
    private String LastName;

    @Column(nullable=false, unique = true)
    private String emailAddress;

    @JsonIgnore
    @Column(nullable=false)
    private String passwordHash;

    private String phoneNumber;

    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Role role = Role.HOMEOWNER;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable=false)
    private boolean emailVerified = false;

}
