package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.dto.user.UpdateUserRequest;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;


    public UserResponse getCurrentUserProfile() {
        return UserResponse.from(currentUserService.getCurrentUser());
    }

    public UserResponse update(UpdateUserRequest updateUserRequest){
        User user = currentUserService.getCurrentUser();
        user.setFirstName(updateUserRequest.firstName());
        user.setLastName(updateUserRequest.lastName());
        user.setPhoneNumber(updateUserRequest.phoneNumber());

        userRepository.save(user);
        return UserResponse.from(user);
    }

    public void changePassword(ChangePasswordRequest newPassword){
        User user = currentUserService.getCurrentUser();
        if(!passwordEncoder.matches(newPassword.currentPassword(), user.getPasswordHash())) throw new UnauthorizedException("Current password is incorrect");
        user.setPasswordHash(passwordEncoder.encode(newPassword.newPassword()));
        userRepository.save(user);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable){
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    public String picture(MultipartFile file) {
        User u = currentUserService.getCurrentUser();
        if (file.isEmpty() || file.getContentType() == null
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())
                || file.getSize() > 5_000_000)
            throw new IllegalArgumentException("Profile picture must be JPG, PNG or WEBP and <= 5MB");
        try {
            Path dir = Paths.get("uploads");
            Files.createDirectories(dir);
            String name = u.getId() + "-" + UUID.randomUUID() + "-"
                    + Paths.get(file.getOriginalFilename()).getFileName();
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            u.setProfilePictureUrl("/uploads/" + name);
            userRepository.save(u);
            return u.getProfilePictureUrl();
        } catch (IOException e) {
            throw new RuntimeException("Could not store file");
        }
    }

    public void deactivate(Long id){
        User user = userRepository.findById(id).orElseThrow(()-> new InformationNotFoundException("User not found"));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        auditLogService.record(currentUserService.getCurrentUser(), "Deactivate User", "User",id,"Admin deactivated the user.");
    }

}
