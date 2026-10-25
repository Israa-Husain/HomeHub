package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.service.BookingWaitlistService;
import com.ga.HomeHub.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
    @PostMapping
    public Map<String,Object> joinWaitlist(@Valid @RequestBody JoinRequest request){
        return response(waitlist.joinWaitlist(request.serviceId(),request.homeId(),request.date(),request.start()));
    }

    @Operation(summary="Get my waitlist")
    @GetMapping
    public List<Map<String,Object>> getMyWaitlist(){
        return waitlist.getMyWaitlist().stream().map(this::response).toList();
    }

    @Operation(summary="Leave waitlist")
    @DeleteMapping("/{id}")
    public void leaveWaitlist(@PathVariable Long id){
        waitlist.leaveWaitlist(id);
    }

    @Operation(summary="Claim waitlist offer")
    @PostMapping("/{id}/claim")
    public Object claimWaitlistOffer(@PathVariable Long id){
        Booking booking=waitlist.claimWaitlistOffer(id);
        return bookings.toResponse(booking);
    }

    private Map<String,Object> response(BookingWaitlist entry){
        return Map.of("id",entry.getId(),"serviceId",entry.getService().getId(),"homeId",entry.getHome().getId(), "date",entry.getRequestedDate().toString(),"start",entry.getRequestedStartTime().toString(),"status",entry.getStatus().name());
    }
}
