package com.ga.HomeHub.notification.repository;

import com.ga.HomeHub.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

//Map the existing notification table and reference recipients by their Auth account IDs.
@Transactional(readOnly=true)
public interface NotificationRepository extends JpaRepository<Notification,Long>{
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id,Long recipientId);

    @Query(
            value="SELECT * FROM notification WHERE recipient_id=:recipientId "
                    +"ORDER BY created_at DESC,id DESC limit :limit offset :offset",
            nativeQuery=true
    )
    List<Notification> history(
            @Param("recipientId") long recipientId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    default long unread(long recipientId){
        return countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead=true,n.updatedAt=:now "
            +"WHERE n.id=:id AND n.recipientId=:recipientId")
    int updateRead(
            @Param("recipientId") long recipientId,
            @Param("id") long id,
            @Param("now") LocalDateTime now
    );

    @Transactional
    default boolean markRead(long recipientId,long id){
        return updateRead(recipientId,id,LocalDateTime.now())>0;
    }

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead=true,n.updatedAt=:now "
            +"WHERE n.recipientId=:recipientId AND n.isRead=false")
    int updateAllRead(
            @Param("recipientId") long recipientId,
            @Param("now") LocalDateTime now
    );

    @Transactional
    default int markAllRead(long recipientId){
        return updateAllRead(recipientId,LocalDateTime.now());
    }

    @Transactional
    default void insert(long recipientId,String message,String type){
        Notification notification=new Notification();
        notification.setRecipientId(recipientId);
        notification.setMessage(message);
        notification.setEventType(type);
        saveAndFlush(notification);
    }
}