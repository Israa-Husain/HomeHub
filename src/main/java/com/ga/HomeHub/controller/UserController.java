package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.dto.user.UpdateUserRequest;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/profile")
    public UserResponse getCurrentUserProfile(){
        return userService.getCurrentUserProfile();
    }

    @PutMapping("/profile")
    public UserResponse updateUserProfile(@RequestBody UpdateUserRequest request){
        return userService.update(request);
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request){
        userService.changePassword(request);
        return ResponseEntity.noContent().build(); //Status: 204 No Content, Body:(empty)
    }

    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadProfilePicture(@RequestParam MultipartFile file){
        return Map.of("url",userService.picture(file));
    }

}
