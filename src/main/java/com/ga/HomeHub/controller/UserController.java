package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.dto.user.UpdateUserRequest;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name="User", description="HomeHub user API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @Operation(summary="Get current user profile")
    @GetMapping("/profile")
    public UserResponse getCurrentUserProfile(){
        return userService.getCurrentUserProfile();
    }

    @Operation(summary="Update user profile")
    @PutMapping("/profile")
    public UserResponse updateUserProfile(@Valid @RequestBody UpdateUserRequest request){
        return userService.update(request);
    }

    @Operation(summary="Change password")
    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request){
        userService.changePassword(request);
        return ResponseEntity.noContent().build(); //Status: 204 No Content, Body:(empty)
    }

    @Operation(summary="Upload profile picture")
    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadProfilePicture(@RequestParam MultipartFile file){
        return Map.of("url",userService.picture(file));
    }

}
