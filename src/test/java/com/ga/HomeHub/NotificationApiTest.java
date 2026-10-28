package com.ga.HomeHub;

import com.ga.HomeHub.controller.NotificationController;
import com.ga.HomeHub.exception.ApiExceptionHandler;
import com.ga.HomeHub.model.Notification;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.repository.NotificationRepository;
import com.ga.HomeHub.repository.UserRepository;
import com.ga.HomeHub.service.CurrentUserService;
import com.ga.HomeHub.service.NotificationService;
import jakarta.servlet.AsyncContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//Exercise the real notification service and SSE emitter with mocked persistence, not a second implementation.
@ExtendWith(MockitoExtension.class)
class NotificationApiTest{
    @Mock private NotificationRepository repository;
    @Mock private UserRepository users;
    @Mock private CurrentUserService current;
    private NotificationService notifications;
    private MockMvc mvc;

    @BeforeEach
    void setUp(){
        notifications=spy(new NotificationService(repository,users,Runnable::run));
        mvc=MockMvcBuilders.standaloneSetup(new NotificationController(notifications,current))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void historyCountsAndReadUpdatesUseCurrentRecipientForEveryRole() throws Exception{
        for(Role role:Role.values()){
            long id=role.ordinal()+1L;
            User user=new User();
            user.setId(id);
            user.setRole(role);
            Notification item=new Notification();
            item.setId(100L+id);
            item.setRecipient(user);
            item.setMessage("Booking update");
            when(current.getCurrentUser()).thenReturn(user);
            when(repository.findByRecipientIdOrderByCreatedAtDesc(id)).thenReturn(List.of(item));
            when(repository.countByRecipientIdAndIsReadFalse(id)).thenReturn(1L);
            when(repository.findByIdAndRecipientId(item.getId(),id)).thenReturn(Optional.of(item));
            mvc.perform(get("/api/notifications")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].message").value("Booking update"))
                    .andExpect(jsonPath("$[0].read").value(false))
                    .andExpect(jsonPath("$[0].recipient").doesNotExist());
            mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1));
            mvc.perform(patch("/api/notifications/"+item.getId()+"/read")).andExpect(status().isOk());
            assertTrue(item.isRead());
            verify(repository).save(item);
            mvc.perform(get("/api/notifications")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].read").value(true));
            when(repository.countByRecipientIdAndIsReadFalse(id)).thenReturn(0L);
            mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
    }

    @Test
    void anotherRecipientsNotificationCannotBeMarkedRead() throws Exception{
        User user=new User();
        user.setId(1L);
        when(current.getCurrentUser()).thenReturn(user);
        when(repository.findByIdAndRecipientId(999L,1L)).thenReturn(Optional.empty());
        mvc.perform(patch("/api/notifications/999/read")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found"));
        verify(repository,never()).save(any());
    }

    @Test
    void realSseEmitterDeliversExistingNamedEventAndCleansUpOnCompletion() throws Exception{
        User user=new User();
        user.setId(1L);
        user.setEmailAddress("private@example.test");
        when(current.getCurrentUser()).thenReturn(user);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(any())).thenAnswer(invocation->{
            Notification item=invocation.getArgument(0);
            item.setId(42L);
            return item;
        });
        AtomicReference<SseEmitter> stream=new AtomicReference<>();
        doAnswer(invocation->{
            SseEmitter emitter=(SseEmitter)invocation.callRealMethod();
            stream.set(emitter);
            return emitter;
        }).when(notifications).subscribeToNotifications(1L);
        var connection=mvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        AsyncContext async=connection.getRequest().getAsyncContext();
        try{
            notifications.sendNotifications(1L,"Booking update");
            String body=connection.getResponse().getContentAsString();
            assertTrue(connection.getResponse().getContentType().startsWith("text/event-stream"));
            assertTrue(body.contains("event:booking"));
            assertTrue(body.contains("id:42"));
            assertTrue(body.contains("data:Booking update"));
            assertFalse(body.contains("private@example.test"));
            verify(repository).save(any());
        }finally{
            stream.get().complete();
            async.complete();
        }
        Map<?,?> clients=(Map<?,?>)ReflectionTestUtils.getField(notifications,"clients");
        assertNotNull(clients);
        assertTrue(clients.isEmpty());
    }
}
