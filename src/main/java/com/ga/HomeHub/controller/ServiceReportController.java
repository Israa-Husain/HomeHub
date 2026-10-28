package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.ServiceReport;
import com.ga.HomeHub.service.ServiceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name="ServiceReport", description="HomeHub servicereport API endpoints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ServiceReportController {
    private final ServiceReportService service;
    public record ReportRequest(@NotBlank @Size(max=3000) String workDone, @Size(max=3000) String materialsUsed, @Size(max=3000) String recommendation){}

    @Operation(summary="Get report")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/booking/{bookingId}")
    public Map<String,Object> getReport(@PathVariable Long bookingId){
        return response(service.getServiceReport(bookingId));
    }

    @Operation(summary="Submit report")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PostMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public Map<String,Object> submitReport(@PathVariable Long bookingId, @Valid @RequestBody ReportRequest request){
        return response(service.submitServiceReport(bookingId, request.workDone(), request.materialsUsed(), request.recommendation()));
    }

    private Map<String,Object> response(ServiceReport report) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", report.getId());
        result.put("bookingId", report.getBooking().getId());
        result.put("workPerformed", report.getWorkPerformed());
        result.put("materialsUsed", report.getMaterialsUsed());
        result.put("recommendation", report.getRecommendations());
        return result;
    }
}
