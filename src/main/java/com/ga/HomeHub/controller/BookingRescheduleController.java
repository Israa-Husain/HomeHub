package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.service.BookingRescheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ga.HomeHub.model.enums.RescheduleStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.HashMap;

@Tag(name="BookingReschedule", description="HomeHub bookingreschedule API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reschedules")
public class BookingRescheduleController {
    private final BookingRescheduleService service;
    public record Request(@NotNull LocalDate date, @NotNull LocalTime start){}
    public record Decision(@NotNull Boolean approve){}

    @Operation(summary="Get current homeowner or provider reschedule requests")
    @GetMapping
    @PreAuthorize("hasAnyRole('HOMEOWNER','PROVIDER')")
    public Page<Map<String,Object>> getCurrentUserRequests(@RequestParam(required=false) RescheduleStatus status,
                                                           @RequestParam(required=false) Long bookingId,Pageable pageable){
        return service.getCurrentUserRequests(status,bookingId,pageable).map(this::response);
    }

    @Operation(summary="Get authorized reschedule request details")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOMEOWNER','PROVIDER')")
    public Map<String,Object> getRequestById(@PathVariable Long id){
        return response(service.getRequestById(id));
    }

    @Operation(summary="Request reschedule")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Map<String,Object> requestReschedule(@PathVariable Long bookingId,@Valid @RequestBody Request request){
        return response(service.request(bookingId,request.date(),request.start()));
    }

    @Operation(summary="Decide reschedule")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PutMapping("/{id}/decision")
    @PreAuthorize("hasRole('PROVIDER')")
    public Map<String,Object> decideReschedule(@PathVariable Long id,@Valid @RequestBody Decision decision){
        return response(service.decide(id,decision.approve()));
    }

    private Map<String,Object> response(BookingRescheduleRequest r){
        Map<String,Object> result=new HashMap<>();
        result.put("id",r.getId());
        result.put("bookingId",r.getBooking().getId());
        result.put("date",r.getRequestedDate().toString());
        result.put("start",r.getRequestedStartTime().toString());
        result.put("status",r.getStatus().name());
        result.put("bookingDate",r.getBooking().getBookingDate());
        result.put("bookingStartTime",r.getBooking().getStartTime());
        result.put("bookingEndTime",r.getBooking().getEndTime());
        result.put("bookingStatus",r.getBooking().getStatus());
        result.put("serviceId",r.getBooking().getService().getId());
        result.put("serviceName",r.getBooking().getService().getName());
        result.put("providerId",r.getBooking().getService().getProvider().getId());
        result.put("providerName",r.getBooking().getService().getProvider().getBusinessName());
        result.put("createdAt",r.getCreatedAt());
        result.put("updatedAt",r.getUpdatedAt());
        return result;
    }
}
