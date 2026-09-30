package com.ga.HomeHub.model;

import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String firstName;

    @Column
    private String LastName;

    @Column
    private String email;

    @Column
    private String passwordHash;

    private String phoneNumber;

    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    @Column
    private Role role = Role.HOMEOWNER;

    @Enumerated(EnumType.STRING)
    @Column
    private UserStatus status = UserStatus.ACTIVE;

    @Column
    private boolean emailVerified = false;


    //SETTERS & GETTERS
    public Long getId() {
        return id;
    }


    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return LastName;
    }

    public void setLastName(String lastName) {
        LastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
