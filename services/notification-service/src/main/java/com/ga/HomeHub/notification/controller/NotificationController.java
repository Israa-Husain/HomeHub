package com.ga.HomeHub.notification.controller;

import com.ga.HomeHub.notification.dto.NotificationResponse;
import com.ga.HomeHub.notification.service.CurrentUserService;
import com.ga.HomeHub.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(
        name="Notifications",
        description="Owned history, unread state and authenticated live updates"
)
@ApiResponses({
        @ApiResponse(responseCode="400",description="Invalid request"),
        @ApiResponse(responseCode="401",description="Authentication required or account inactive"),
        @ApiResponse(responseCode="403",description="Access denied"),
        @ApiResponse(responseCode="503",description="Account verification or database unavailable")
})
public class NotificationController{
    private final NotificationService notifications;
    private final CurrentUserService current;

    public NotificationController(
            NotificationService notifications,
            CurrentUserService current
    ){
        this.notifications=notifications;
        this.current=current;
    }

    @GetMapping
    @Operation(summary="Read own notification history, newest first")
    @ApiResponse(responseCode="200",description="Owned notification history")
    public List<NotificationResponse> getHistory(
            @RequestParam(defaultValue="200") int limit,
            @RequestParam(defaultValue="0") int offset
    ){
        return notifications.history(
                current.getCurrentUser().id(),
                limit,
                offset
        );
    }

    @GetMapping("/unread-count")
    @Operation(summary="Count own unread notifications")
    @ApiResponse(responseCode="200",description="Unread count")
    public Map<String,Long> getUnreadCount(){
        return Map.of(
                "count",
                notifications.unread(current.getCurrentUser().id())
        );
    }

    @PatchMapping("/{id}/read")
    @Operation(summary="Mark an owned notification read; repeating the request is safe")
    @ApiResponses({
            @ApiResponse(responseCode="200",description="Notification marked read"),
            @ApiResponse(responseCode="404",description="Not found or belongs to another account")
    })
    public void markRead(@PathVariable long id){
        notifications.markRead(current.getCurrentUser().id(),id);
    }

    @PatchMapping("/read-all")
    @Operation(summary="Mark all own notifications read")
    @ApiResponse(responseCode="200",description="Number of notifications updated")
    public Map<String,Integer> markAllRead(){
        return Map.of(
                "updated",
                notifications.markAllRead(current.getCurrentUser().id())
        );
    }

    @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary="Subscribe to own live updates; refresh history on reconnect and refresh events")
    @ApiResponses({
            @ApiResponse(responseCode="200",description="Authenticated SSE stream"),
            @ApiResponse(responseCode="429",description="Per-account connection limit reached")
    })
    public SseEmitter subscribeToNotifications(
            @RequestHeader("Authorization") String bearer
    ){
        return notifications.subscribe(current.getCurrentUser(),bearer);
    }
}