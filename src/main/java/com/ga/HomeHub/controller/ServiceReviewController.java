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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public record ReviewRequest(@NotNull Long bookingId, @Min(1) @Max(5) int rating, @Size(max=3000) String comment){}

    @Operation(summary="Get current homeowner's submitted reviews")
    @GetMapping("/mine")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Page<Map<String,Object>> getMyReviews(Pageable pageable){
        return service.getMyReviews(pageable).map(review->response(review,true));
    }

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
        return response(service.createServiceReviw(request.bookingId(), request.rating(), request.comment()),true);
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
        return service.providerReviews(providerId).stream().map(review->response(review,false)).toList();
    }

    private Map<String,Object> response(ServiceReview review,boolean ownReview){
        Map<String,Object> result = new HashMap<>();
        result.put("id", review.getId());
        if(ownReview){
            result.put("bookingId",review.getBooking().getId());
            result.put("serviceId",review.getBooking().getService().getId());
            result.put("serviceName",review.getBooking().getService().getName());
        }
        result.put("rating", review.getRating());
        result.put("providerId", review.getBooking().getService().getProvider().getId());
        result.put("comment", review.getComment());
        result.put("createdAt",review.getCreatedAt());
        return result;
    }
}
