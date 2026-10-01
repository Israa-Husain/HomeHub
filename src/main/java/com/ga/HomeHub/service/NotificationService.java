package com.ga.HomeHub.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {
    private final ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>> clients = new ConcurrentHashMap<>();

    public SseEmitter subscribeToNotifications(Long userId){
        SseEmitter e = new SseEmitter(0L);
        clients.computeIfAbsent(userId, k-> new CopyOnWriteArrayList<>()).add(e);
        e.onCompletion(()-> clients.getOrDefault(userId, new CopyOnWriteArrayList<>()).remove(e));
        return e;
    }

    public void sendNotifications(Long userId, String message){
        for(SseEmitter e: clients.getOrDefault(userId, new CopyOnWriteArrayList<>())){
            try{
                e.send(SseEmitter.event().name("booking").data(message));
            } catch (IOException x){
                e.complete();
            }
        }
    }
}
