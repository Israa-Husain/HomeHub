package com.ga.HomeHub.notification.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidation implements ApplicationRunner{
    private final JdbcTemplate jdbc;

    public SchemaValidation(JdbcTemplate jdbc){
        this.jdbc=jdbc;
    }

    @Override
    public void run(ApplicationArguments args){
        //Validate required existing columns without creating or changing database objects.
        jdbc.queryForList(
                "select id,recipient_id,message,is_read,event_type,created_at,updated_at "
                        +"from notification where 1=0"
        );

        jdbc.queryForList(
                "select id,topic,event_key,payload,created_at,published_at,attempts "
                        +"from outbox_event where 1=0"
        );
    }
}