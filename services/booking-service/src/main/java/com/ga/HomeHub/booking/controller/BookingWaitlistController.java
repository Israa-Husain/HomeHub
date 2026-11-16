package com.ga.HomeHub.booking.controller;

import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingWaitlist;
import com.ga.HomeHub.booking.service.BookingWaitlistService;
import com.ga.HomeHub.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Tag(name="BookingWaitlist", description="HomeHub bookingwaitlist API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/waitlist")
@PreAuthorize("hasRole('HOMEOWNER')")
public class BookingWaitlistController {
    private final BookingWaitlistService waitlist;
    private final BookingService bookings;
    public record JoinRequest(@NotNull Long serviceId,@NotNull Long homeId,@NotNull LocalDate date,@NotNull LocalTime start){}

    @Operation(summary="Join waitlist")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PostMapping
    public Map<String,Object> joinWaitlist(@Valid @RequestBody JoinRequest request){
        return response(waitlist.joinWaitlist(request.serviceId(),request.homeId(),request.date(),request.start()));
    }

    @Operation(summary="Get my waitlist")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @GetMapping
    public List<Map<String,Object>> getMyWaitlist(){
        return waitlist.getMyWaitlist().stream().map(this::response).toList();
    }

    @Operation(summary="Leave waitlist")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @DeleteMapping("/{id}")
    public void leaveWaitlist(@PathVariable Long id){
        waitlist.leaveWaitlist(id);
    }

    @Operation(summary="Claim waitlist offer")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found"),
            @ApiResponse(responseCode="503",description="Account verification unavailable")
    })
    @PostMapping("/{id}/claim")
    public Object claimWaitlistOffer(@PathVariable Long id){
        Booking booking=waitlist.claimWaitlistOffer(id);
        return bookings.toResponse(booking);
    }

    private Map<String,Object> response(BookingWaitlist entry){
        Map<String,Object> result=new HashMap<>();
        result.put("id",entry.getId());
        result.put("serviceId",entry.getService().getId());
        result.put("homeId",entry.getHome().getId());
        result.put("date",entry.getRequestedDate().toString());
        result.put("start",entry.getRequestedStartTime().toString());
        result.put("status",entry.getStatus().name());
        result.put("offerExpiresAt",entry.getOfferExpiresAt());
        return result;
    }
}
