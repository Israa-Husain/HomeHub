package com.ga.HomeHub.controller;

import com.ga.HomeHub.service.CurrentUserService;
import com.ga.HomeHub.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Tag(name="Notification", description="HomeHub notification API endpoints")
@RestController
@AllArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    @Operation(summary="Subscribe to notifications")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToNotifications(){
        return notificationService.subscribeToNotifications(currentUserService.getCurrentUser().getId());
    }

    @Operation(summary="Get history")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping
    public List<Map<String, Object>> getHistory(){
        return notificationService.history(currentUserService.getCurrentUser().getId()).stream().map(n->Map.<String,Object>of("id",n.getId(),"message",n.getMessage(),"read",n.isRead(),"type",n.getEventType())).toList();
    }

    @Operation(summary="Get unread count")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @GetMapping("/unread-count")
    public Map<String,Long> getUnreadCount(){
        return Map.of("count",notificationService.unread(currentUserService.getCurrentUser().getId()));
    }

    @Operation(summary="Mark read")
    @ApiResponses(value={
            @ApiResponse(responseCode="200",description="Request completed successfully"),
            @ApiResponse(responseCode="400",description="Invalid request"),
            @ApiResponse(responseCode="401",description="Authentication required"),
            @ApiResponse(responseCode="403",description="Access denied"),
            @ApiResponse(responseCode="404",description="Resource not found")
    })
    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable Long id){
        notificationService.markRead(currentUserService.getCurrentUser().getId(),id);
    }
}
