package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.ServiceFavorite;
import com.ga.HomeHub.service.ServiceFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/favorites")
@PreAuthorize("hasRole('HOMEOWNER')")
public class ServiceFavoriteController {
    private final ServiceFavoriteService service;

    @PostMapping("/{serviceId}")
    public Map<String,Object> addFavorite(@PathVariable Long serviceId){
        return response(service.addFavorite(serviceId));
    }

    @GetMapping
    public List<Map<String,Object>> getMyFavorites(){
        return service.getMyFavorite().stream().map(this::response).toList();
    }

    @DeleteMapping("/{favoriteId}")
    public void removefavorite(@PathVariable Long favoriteId){
        service.removeFavorite(favoriteId);
    }

    private Map<String,Object> response(ServiceFavorite favorite){
        return Map.of("id",favorite.getId(), "serviceId",favorite.getService().getId());
    }
}
