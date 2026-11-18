package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.dto.NotificationResponse;
import com.ga.HomeHub.notification.exception.ApiException;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.ga.HomeHub.notification.security.MyUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class NotificationService{
    private final NotificationRepository repository;
    private final LiveNotifications live;

    public NotificationService(
            NotificationRepository repository,
            LiveNotifications live
    ){
        this.repository=repository;
        this.live=live;
    }

    @Transactional(readOnly=true)
    public List<NotificationResponse> history(long userId,int limit,int offset){
        if(limit<1 || limit>200 || offset<0){
            throw new IllegalArgumentException(
                    "Limit must be 1–200 and offset non-negative"
            );
        }

        return repository.history(userId,limit,offset)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly=true)
    public long unread(long userId){
        return repository.unread(userId);
    }

    @Transactional
    public void markRead(long userId,long id){
        if(id<=0){
            throw new IllegalArgumentException("Notification ID must be positive");
        }

        if(!repository.markRead(userId,id)){
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Notification not found"
            );
        }
    }

    @Transactional
    public int markAllRead(long userId){
        return repository.markAllRead(userId);
    }

    public SseEmitter subscribe(MyUserDetails user,String bearer){
        return live.subscribe(user,bearer);
    }
}