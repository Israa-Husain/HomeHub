package com.ga.HomeHub.dto.auth;

import com.ga.HomeHub.model.enums.Role;

public record RegisterRequest(String firstname, String lastName, String email, String password, String phoneNumber, Role role) {
}
