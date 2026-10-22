package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.model.Notification;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.NotificationRepository;
import com.ga.HomeHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final UserRepository users;
    private final Executor executor;

    //Store active SSE connections safely when multiple users connect concurrently.
    private final Map<Long,CopyOnWriteArrayList<SseEmitter>> clients=new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository repository, UserRepository users, @Qualifier("homeHubExecutor") Executor executor){
        this.repository=repository;
        this.users=users;
        this.executor=executor;
    }

    //Create an SSE connection so the user can receive live notifications.
    public SseEmitter subscribeToNotifications(Long userId){
        SseEmitter emitter=new SseEmitter(30*60*1000L);

        clients.computeIfAbsent(userId,id->new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(()->remove(userId,emitter));
        emitter.onTimeout(()->remove(userId,emitter));
        emitter.onError(e->remove(userId,emitter));

        return emitter;
    }

    //Save notifications so users can retrieve messages received while offline.
    @Transactional
    public void sendNotifications(Long userId, String message){
        User user=users.findById(userId).orElseThrow(()->new InformationNotFoundException("Notification recipient not found"));

        Notification notification=new Notification();
        notification.setRecipient(user);
        notification.setMessage(message);
        notification.setEventType("booking");
        repository.save(notification);

        //Send the live notification only after the database transaction commits.
        if(TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override
                public void afterCommit(){
                    executor.execute(()->deliver(userId,notification.getId(),message));
                }
            });
        }else{
            executor.execute(()->deliver(userId,notification.getId(),message));
        }
    }

    //Send the notification to every active connection belonging to the user.
    private void deliver(Long userId, Long eventId, String message){
        List<SseEmitter> userClients=clients.get(userId);

        if(userClients==null){
            return;
        }

        for(SseEmitter emitter:userClients){
            try{
                emitter.send(SseEmitter.event().id(String.valueOf(eventId)).name("booking").data(message));
            }catch(IOException | IllegalStateException e){
                remove(userId,emitter);
                emitter.complete();
            }
        }
    }

    //Remove disconnected clients and clean up empty connection lists.
    private void remove(Long userId, SseEmitter emitter){
        clients.computeIfPresent(userId,(id,list)->{
            list.remove(emitter);

            if(list.isEmpty()){
                return null;
            }

            return list;
        });
    }

    //Retrieve previously saved notifications with the newest ones first.
    @Transactional(readOnly=true)
    public List<Notification> history(Long userId){
        return repository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    //Count notifications that the user has not marked as read.
    @Transactional(readOnly=true)
    public long unread(Long userId){
        return repository.countByRecipientIdAndIsReadFalse(userId);
    }

    //Allow users to mark only their own notifications as read.
    @Transactional
    public void markRead(Long userId, Long id){
        Notification notification=repository.findByIdAndRecipientId(id,userId).orElseThrow(()->new InformationNotFoundException("Notification not found"));

        notification.setRead(true);
        repository.save(notification);
    }
}