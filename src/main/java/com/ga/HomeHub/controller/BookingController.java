package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.BookingRequest;
import com.ga.HomeHub.dto.BookingResponse;
import com.ga.HomeHub.dto.BookingStatusRequest;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name="Booking", description="HomeHub booking API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/booking")
public class BookingController {
    private final BookingService bookingService;

    @Operation(summary="Create booking")
    @ApiResponses(value={
            @ApiResponse(responseCode="201",description="Booking created successfully"),
            @ApiResponse(responseCode="400",description="Invalid booking request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Home or service not found"),
            @ApiResponse(responseCode="422",description="Booking conflict or business rule violation")
    })
    @PostMapping
    @PreAuthorize("hasRole('HOMEOWNER')")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request){
        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.status(201).body(bookingService.toResponse(booking));
    }

    @Operation(summary="Get current user bookings")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="User bookings retrieved successfully"),
            @ApiResponse(responseCode="400",description="Invalid booking status or pagination parameters"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied")
    })
    @GetMapping
    public Page<BookingResponse> getCurrentUserBookings(@RequestParam(required = false)BookingStatus status, Pageable pageable){
        return bookingService.getCurrentUserBookings(status, pageable).map(bookingService::toResponse);
    }

    @Operation(summary="Discover currently available appointment start times; no slot is reserved")
    @GetMapping("/available-times")
    public List<LocalTime> getAvailableAppointmentTimes(@RequestParam Long serviceId,@RequestParam LocalDate date,
                                                       @RequestParam(defaultValue="NORMAL") BookingType bookingType){
        return bookingService.getAvailableAppointmentTimes(serviceId,date,bookingType);
    }

    @Operation(summary="Get recorded booking audit events for an authorized participant or admin")
    @GetMapping("/{bookingId}/history")
    public Page<Map<String,Object>> getBookingHistory(@PathVariable Long bookingId,Pageable pageable){
        return bookingService.getBookingHistory(bookingId,pageable).map(this::historyResponse);
    }

    @Operation(summary="Get booking by id")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Booking retrieved successfully"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Not authorized to view this booking"),
            @ApiResponse(responseCode="404",description="Booking not found")
    })
    @GetMapping("/{bookingId}")
    public BookingResponse getBookingById(@PathVariable Long bookingId) {
        return bookingService.toResponse(bookingService.getBookingById(bookingId));
    }

    @Operation(summary="Cancel booking")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Booking cancelled successfully; updated booking returned"),
            @ApiResponse(responseCode="400",description="Invalid cancellation request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Not authorized to cancel this booking"),
            @ApiResponse(responseCode="404",description="Booking not found"),
            @ApiResponse(responseCode="422",description="Booking cannot be cancelled in its current status")
    })
    @DeleteMapping("/{bookingId}")
    public BookingResponse cancelBooking(@PathVariable Long bookingId){
        return bookingService.toResponse(bookingService.cancelBooking(bookingId));
    }

    @Operation(summary="Update booking status")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Booking status updated successfully"),
            @ApiResponse(responseCode="400",description="Invalid booking status request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Admin or assigned provider access required"),
            @ApiResponse(responseCode="404",description="Booking not found"),
            @ApiResponse(responseCode="422",description="Invalid status transition or required service report missing")
    })
    @PutMapping("/{bookingId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PROVIDER')")
    public BookingResponse updateBookingStatus(@PathVariable Long bookingId, @Valid @RequestBody BookingStatusRequest request){
        return bookingService.toResponse(bookingService.updateBookingStatus(bookingId, request.status()));
    }

    //Do not return audit descriptions, linked user accounts, or private actor contact information.
    private Map<String,Object> historyResponse(AuditLog event){
        Map<String,Object> result=new HashMap<>();
        result.put("id",event.getId());
        result.put("action",event.getAction());
        result.put("recordedAt",event.getCreatedAt());
        result.put("actorId",event.getUser()==null?null:event.getUser().getId());
        result.put("actorRole",event.getUser()==null?null:event.getUser().getRole());
        BookingStatus status=null;
        if("Create Booking".equals(event.getAction())){
            status=BookingStatus.PENDING;
        }else if("Cancel Booking".equals(event.getAction())){
            status=BookingStatus.CANCELLED;
        }else if("Update Booking status".equals(event.getAction())){
            for(BookingStatus value:BookingStatus.values()){
                if(("Booking status changed to "+value).equals(event.getDescription())){
                    status=value;
                    break;
                }
            }
        }
        result.put("status",status);
        return result;
    }
}
