package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.service.BookingWaitlistService;
import com.ga.HomeHub.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/waitlist")
@PreAuthorize("hasRole('HOMEOWNER')")
public class BookingWaitlistController {
    private final BookingWaitlistService waitlist;
    private final BookingService bookings;
    public record JoinRequest(@NotNull Long serviceId,@NotNull Long homeId,@NotNull LocalDate date,@NotNull LocalTime start){}

    @PostMapping
    public Map<String,Object> joinWaitlist(@Valid @RequestBody JoinRequest request){
        return response(waitlist.joinWaitlist(request.serviceId(),request.homeId(),request.date(),request.start()));
    }

    @GetMapping
    public List<Map<String,Object>> getMyWaitlist(){
        return waitlist.getMyWaitlist().stream().map(this::response).toList();
    }

    @DeleteMapping("/{id}")
    public void leaveWaitlist(@PathVariable Long id){
        waitlist.leaveWaitlist(id);
    }

    @PostMapping("/{id}/claim")
    public Object claimWaitlistOffer(@PathVariable Long id){
        Booking booking=waitlist.claimWaitlistOffer(id);
        return bookings.toResponse(booking);
    }

    private Map<String,Object> response(BookingWaitlist entry){
        return Map.of("id",entry.getId(),"serviceId",entry.getService().getId(),"homeId",entry.getHome().getId(), "date",entry.getRequestedDate().toString(),"start",entry.getRequestedStartTime().toString(),"status",entry.getStatus().name());
    }
}
