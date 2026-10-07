package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.HomeRequest;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.service.HomeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/homes")
public class HomeController {
    private final HomeService homeService;

    @PostMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public ResponseEntity<Home> createHome(@Valid @RequestBody HomeRequest request){
        return ResponseEntity.status(201).body(homeService.createHome(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Page<Home> getCurrentUserHomes(Pageable pageable){
        return homeService.getCurrentUserHomes(pageable);
    }

    @GetMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Home getHomeById(@PathVariable Long homeId){
        return homeService.getHomeById(homeId);
    }

    @PutMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Home updateHome(@PathVariable Long homeId, @Valid @RequestBody HomeRequest request){
        return homeService.updateHome(homeId, request);
    }

    @DeleteMapping("/{homeId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHome(@PathVariable Long homeId){
        homeService.deleteHome(homeId);
    }
}
