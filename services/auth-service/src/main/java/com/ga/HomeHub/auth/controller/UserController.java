package com.ga.HomeHub.auth.controller;

import com.ga.HomeHub.auth.dto.auth.ChangePasswordRequest;
import com.ga.HomeHub.auth.dto.user.UpdateUserRequest;
import com.ga.HomeHub.auth.dto.user.UserResponse;
import com.ga.HomeHub.auth.exception.InformationNotFoundException;
import com.ga.HomeHub.auth.repository.UserRepository;
import com.ga.HomeHub.auth.service.CurrentUserService;
import com.ga.HomeHub.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name="User",description="HomeHub user API endpoints")
@RestController
@AllArgsConstructor
public class UserController{
    private final UserService userService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Operation(summary="Get current user profile")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Current user profile returned"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Account is inactive or access is denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @GetMapping("/api/users/profile")
    public UserResponse getCurrentUserProfile(){
        return userService.getCurrentUserProfile();
    }

    @Operation(summary="Update user profile")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="User profile updated"),
            @ApiResponse(responseCode="400",description="Invalid profile data or request body"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Account is inactive or access is denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="409",description="Profile data conflicts with an existing account"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @PutMapping("/api/users/profile")
    public UserResponse updateUserProfile(@Valid @RequestBody UpdateUserRequest request){
        return userService.update(request);
    }

    @Operation(summary="Change password")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Password changed; no response body"),
            @ApiResponse(responseCode="400",description="Invalid password data or request body"),
            @ApiResponse(responseCode="401",description="Authentication failed"),
            @ApiResponse(responseCode="403",description="Account is inactive or access is denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @PutMapping("/api/users/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request){
        userService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary="Upload profile picture")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Profile picture uploaded; image URL returned"),
            @ApiResponse(responseCode="400",description="Missing, empty or invalid image"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Account is inactive or access is denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="413",description="Upload exceeds the configured size limit"),
            @ApiResponse(responseCode="415",description="Request content type is not supported"),
            @ApiResponse(responseCode="500",description="Unexpected server or storage error")
    })
    @PostMapping(value="/api/users/profile-picture",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadProfilePicture(@RequestParam("file") MultipartFile file){
        return Map.of("url",userService.picture(file));
    }

    @Operation(summary="List accounts")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Paginated account list returned"),
            @ApiResponse(responseCode="400",description="Invalid request parameters"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Administrator role required"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/users")
    public Page<UserResponse> list(Pageable pageable){
        return userService.getAllUsers(pageable);
    }

    @Operation(summary="Deactivate an account immediately")
    @ApiResponses(value={
            @ApiResponse(responseCode="204",description="Account deactivated; no response body"),
            @ApiResponse(responseCode="400",description="Invalid account ID"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Administrator role required"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id){
        userService.deactivate(id);
    }

    //Resolve the authenticated account for other services without exposing credentials.
    @Operation(summary="Resolve a live account from its bearer token")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Current account metadata returned"),
            @ApiResponse(responseCode="401",description="Missing or invalid authentication token"),
            @ApiResponse(responseCode="403",description="Account is inactive or access is denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @GetMapping("/internal/users/current")
    public UserResponse current(){
        return UserResponse.from(currentUserService.getCurrentUser());
    }

    //The internal API filter must enforce the service credential for this endpoint.
    @Operation(summary="Read account metadata; requires the internal service credential")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Account metadata returned"),
            @ApiResponse(responseCode="400",description="Invalid account ID"),
            @ApiResponse(responseCode="401",description="Missing or invalid internal service credential"),
            @ApiResponse(responseCode="403",description="Internal service access denied"),
            @ApiResponse(responseCode="404",description="User not found"),
            @ApiResponse(responseCode="500",description="Unexpected server error")
    })
    @SecurityRequirement(name="internalKey")
    @GetMapping("/internal/users/{id}")
    public UserResponse byId(@PathVariable Long id){
        return UserResponse.from(userRepository.findById(id).orElseThrow(()->new InformationNotFoundException("User not found")));
    }
}