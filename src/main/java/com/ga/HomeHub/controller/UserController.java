package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.dto.user.UpdateUserRequest;
import com.ga.HomeHub.dto.user.UserResponse;
import com.ga.HomeHub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/profile")
    public UserResponse getCurrentUserProfile(){
        return userService.getCurrentUserProfile();
    }

    @Operation(summary="Update user profile")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PutMapping("/profile")
    public UserResponse updateUserProfile(@Valid @RequestBody UpdateUserRequest request){
        return userService.update(request);
    }

    @Operation(summary="Change password")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Password changed; no response body"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request){
        userService.changePassword(request);
        return ResponseEntity.noContent().build(); //Status: 204 No Content, Body:(empty)
    }

    @Operation(summary="Upload profile picture")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadProfilePicture(@RequestParam MultipartFile file){
        return Map.of("url",userService.picture(file));
    }

}
