package com.ga.HomeHub.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    //the Map stores user id and their open notification connections
    private final Map<Long, List<SseEmitter>> clients = new HashMap<>();

    public SseEmitter subscribeToNotifications(Long userId) {

        SseEmitter emitter = new SseEmitter(0L);

        if(!clients.containsKey(userId)){
            clients.put(userId, new ArrayList<>());
        }

        clients.get(userId).add(emitter);
        emitter.onCompletion(() -> {clients.get(userId).remove(emitter);});
        emitter.onTimeout(() -> {clients.get(userId).remove(emitter);});
        return emitter;
    }

    public void sendNotifications(Long userId, String message) {

        if(!clients.containsKey(userId)){
            return;
        }

        List<SseEmitter> userClients = clients.get(userId);

        for(SseEmitter emitter : userClients){
            try{
                emitter.send(SseEmitter.event().name("booking").data(message));
            } catch (IOException exception){
                emitter.complete();
            }
        }
    }
}