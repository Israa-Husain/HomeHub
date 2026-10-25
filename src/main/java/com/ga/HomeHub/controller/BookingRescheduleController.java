package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.service.BookingRescheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Tag(name="BookingReschedule", description="HomeHub bookingreschedule API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reschedules")
public class BookingRescheduleController {
    private final BookingRescheduleService service;
    public record Request(@NotNull LocalDate date, @NotNull LocalTime start){}
    public record Decision(@NotNull boolean approve){}

    @Operation(summary="Request reschedule")
    @PostMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('HOMEOWNER')")
    public Map<String,Object> requestReschedule(@PathVariable Long bookingId,@Valid @RequestBody Request request){
        return response(service.request(bookingId,request.date(),request.start()));
    }

    @Operation(summary="Decide reschedule")
    @PutMapping("/{id}/decision")
    @PreAuthorize("hasRole('PROVIDER')")
    public Map<String,Object> decideReschedule(@PathVariable Long id,@Valid @RequestBody Decision decision){
        return response(service.decide(id,decision.approve()));
    }

    private Map<String,Object> response(BookingRescheduleRequest r){
        return Map.of("id",r.getId(),"bookingId",r.getBooking().getId(),"date",r.getRequestedDate().toString(), "start",r.getRequestedStartTime().toString(),"status",r.getStatus().name());
    }
}
