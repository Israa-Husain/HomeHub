package com.ga.HomeHub.notification.service;

import com.ga.HomeHub.notification.dto.NotificationResponse;
import com.ga.HomeHub.notification.exception.ApiException;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import com.ga.HomeHub.notification.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LiveNotifications{
    private final ConcurrentHashMap<Long,CopyOnWriteArrayList<Connection>> clients=
            new ConcurrentHashMap<>();

    private final NotificationRepository repository;
    private final AccountVerificationService accounts;
    private final Executor executor;
    private final int maxPerUser;

    public LiveNotifications(
            NotificationRepository repository,
            AccountVerificationService accounts,
            @Qualifier("homeHubExecutor") Executor executor,
            @Value("${homehub.sse.max-per-user:5}") int maxPerUser
    ){
        this.repository=repository;
        this.accounts=accounts;
        this.executor=executor;
        this.maxPerUser=maxPerUser;
    }

    //Keep notifications in the database so users can retrieve them after reconnecting.
    public SseEmitter subscribe(MyUserDetails user,String bearer){
        SseEmitter emitter=new SseEmitter(1800000L);
        Connection connection=new Connection(emitter,bearer);

        clients.compute(user.id(),(id,list)->{
            if(list==null){
                list=new CopyOnWriteArrayList<>();
            }

            if(list.size()>=maxPerUser){
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many live connections");
            }

            list.add(connection);
            return list;
        });

        Runnable cleanup=()->clients.computeIfPresent(user.id(),(id,list)->{
            list.remove(connection);
            return list.isEmpty()?null:list;
        });

        emitter.onCompletion(cleanup);

        emitter.onTimeout(()->{
            cleanup.run();
            emitter.complete();
        });

        emitter.onError(error->cleanup.run());

        return emitter;
    }

    @Scheduled(fixedDelayString="${homehub.sse.poll-ms:5000}")
    public void poll(){
        clients.forEach((userId,connections)->connections.forEach(connection->{
            if(!connection.busy.compareAndSet(false,true)){
                return;
            }

            try{
                executor.execute(()->{
                    try{
                        deliver(userId,connection);
                    }finally{
                        connection.busy.set(false);
                    }
                });
            }catch(RejectedExecutionException e){
                connection.busy.set(false);
            }
        }));
    }

    private void deliver(Long userId,Connection connection){
        try{
            //Recheck tokens and account activation during long-lived streams, on every poll.
            MyUserDetails verified=accounts.verify(connection.bearer);

            if(!userId.equals(verified.id())){
                close(userId,connection);
                return;
            }

            List<NotificationResponse> history=repository.history(userId,200,0)
                    .stream()
                    .map(NotificationResponse::from)
                    .toList();

            synchronized(connection){
                Set<Long> visible=new HashSet<>();

                for(int i=history.size()-1;i>=0;i--){
                    NotificationResponse notification=history.get(i);
                    visible.add(notification.id());

                    if(!connection.seen.contains(notification.id())){
                        connection.emitter.send(
                                SseEmitter.event()
                                        .id(notification.id().toString())
                                        .name("booking")
                                        .data(notification)
                        );
                    }
                }

                connection.seen.clear();
                connection.seen.addAll(visible);

                //Clients refresh paginated history on reconnect and refresh events, including large bursts.
                connection.emitter.send(
                        SseEmitter.event()
                                .name("refresh")
                                .data(Map.of(
                                        "unreadCount",
                                        repository.unread(userId)
                                ))
                );
            }
        }catch(ApiException e){
            close(userId,connection);
        }catch(IOException | IllegalStateException e){
            close(userId,connection);
        }catch(RuntimeException e){
            //Transient DB failures do not erase history or terminate other users' polling tasks.
        }
    }

    private void close(Long userId,Connection connection){
        clients.computeIfPresent(userId,(id,list)->{
            list.remove(connection);
            return list.isEmpty()?null:list;
        });

        connection.emitter.complete();
    }

    private static class Connection{
        private final SseEmitter emitter;
        private final String bearer;
        private final Set<Long> seen=new HashSet<>();
        private final AtomicBoolean busy=new AtomicBoolean();

        private Connection(SseEmitter emitter,String bearer){
            this.emitter=emitter;
            this.bearer=bearer;
        }
    }
}