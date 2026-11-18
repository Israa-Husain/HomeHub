package com.ga.HomeHub.booking.model;

import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

@Entity
@Data
@Immutable
@Table(name = "users")
public class User extends BaseEntity{
    @Id
    private Long id;

    @Column(nullable=false)
    private String firstName;

    @Column(nullable=false)
    private String LastName;

    @Column(nullable=false, unique = true)
    private String emailAddress;

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