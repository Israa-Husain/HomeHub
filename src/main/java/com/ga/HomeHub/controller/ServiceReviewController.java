package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.ServiceReview;
import com.ga.HomeHub.service.ServiceReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name="ServiceReview", description="HomeHub servicereview API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ServiceReviewController {
    private final ServiceReviewService service;
    public record ReviewRequest(@NotNull Long bookingId, @Min(1) @Max(5) int rating, String comment){}

    @Operation(summary="Create review")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Map<String,Object> createReview(@Valid @RequestBody ReviewRequest request){
        return response(service.createServiceReviw(request.bookingId(), request.rating(), request.comment()));
    }

    @Operation(summary="Get provider")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/provider/{providerId}")
    public List<Map<String,Object>> getProvider(@PathVariable Long providerId){
        return service.providerReviews(providerId).stream().map(this::response).toList();
    }

    private Map<String,Object> response(ServiceReview review){
        Map<String,Object> result = new HashMap<>();
        result.put("id", review.getId());
        result.put("bookingId", review.getBooking().getId());
        result.put("rating", review.getRating());
        result.put("providerId", review.getBooking().getService().getProvider().getId());
        result.put("comment", review.getComment());
        return result;
    }
}
