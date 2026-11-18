package com.ga.HomeHub.notification.seed;

import com.ga.HomeHub.notification.repository.EventReceiptRepository;
import com.ga.HomeHub.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@ConditionalOnProperty(name="homehub.seed.enabled",havingValue="true")
public class NotificationDataSeed implements CommandLineRunner{
    private final JdbcTemplate jdbc;
    private final EventReceiptRepository receipts;
    private final NotificationRepository notifications;
    private final String emails;

    public NotificationDataSeed(
            JdbcTemplate jdbc,
            EventReceiptRepository receipts,
            NotificationRepository notifications,
            @Value("${homehub.seed.emails:}") String emails
    ){
        this.jdbc=jdbc;
        this.receipts=receipts;
        this.notifications=notifications;
        this.emails=emails;
    }

    @Override
    @Transactional
    public void run(String... args){
        if(emails.isBlank()){
            throw new IllegalArgumentException(
                    "Configure existing demo recipient emails"
            );
        }

        jdbc.execute("select pg_advisory_xact_lock(70448423004)");

        for(String address:emails.split(",")){
            Long id=jdbc.queryForObject(
                    "select id from users where email_address=? "
                            +"and status='ACTIVE' and email_verified=true",
                    Long.class,
                    address.trim().toLowerCase(Locale.ROOT)
            );

            if(id==null){
                throw new IllegalArgumentException(
                        "An existing verified seed account is required"
                );
            }

            if(!receipts.claim("seed-v1-"+id,"notification-demo-v1")){
                continue;
            }

            notifications.insert(
                    id,
                    "Demo: your booking was confirmed",
                    "booking"
            );

            notifications.insert(
                    id,
                    "Demo: an appointment reminder is available",
                    "booking"
            );
        }
    }
}