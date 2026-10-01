package com.ga.HomeHub.dto.user;

import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;


public record UserResponse(Long id, String firstName, String lastName, String email, String phoneNumber, String profilePictureUrl, Role role, UserStatus status, boolean isEmailVerified, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhoneNumber(), user.getProfilePictureUrl(), user.getRole(), user.getStatus(), user.isEmailVerified(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
