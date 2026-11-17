package com.ga.notification.cucumber;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class NotificationTestData{
    public static final String TOPIC="homehub.notifications.v1";
    public static final String DLT="homehub.notifications.v1.DLT";

    private final ScenarioState state;
    private final JdbcTemplate jdbc;
    private final DataSource source;
    private final KafkaTemplate<String,String> kafka;
    private final EmbeddedKafkaBroker broker;
    private final ObjectMapper mapper=new ObjectMapper();

    public NotificationTestData(
            ScenarioState state,
            JdbcTemplate jdbc,
            DataSource source,
            KafkaTemplate<String,String> kafka,
            EmbeddedKafkaBroker broker
    ){
        this.state=state;
        this.jdbc=jdbc;
        this.source=source;
        this.kafka=kafka;
        this.broker=broker;
    }

    public void clean(){
        //Allow cleanup only in the isolated H2 database.
        try(Connection connection=source.getConnection()){
            if(!connection.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:homehub-notification-cucumber")){
                throw new IllegalStateException(
                        "Only the isolated H2 Cucumber database may be cleaned"
                );
            }
        }catch(SQLException e){
            throw new IllegalStateException(
                    "Cannot validate test datasource",
                    e
            );
        }

        jdbc.update("delete from notification");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from users");
    }

    public void users(){
        jdbc.update("insert into users(id) values(1),(2)");
    }

    public long count(String table){
        return jdbc.queryForObject(
                "select count(*) from "+table,
                Long.class
        );
    }

    public void newEvent(String type){
        state.setEvent(new NotificationEvent(
                UUID.randomUUID().toString(),
                1,
                type,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "EMAIL".equals(type)?null:1L,
                "user1@homehub.example",
                "HomeHub Cucumber",
                "Cucumber event "+UUID.randomUUID()
        ));

        encode();
    }

    public void encode(){
        try{
            state.setPayload(
                    mapper.writeValueAsString(state.getEvent())
            );
        }catch(JsonProcessingException e){
            throw new IllegalStateException(
                    "Cannot serialize fixture event",
                    e
            );
        }
    }

    public void publish(){
        String key=state.getEvent()==null
                ?UUID.randomUUID().toString()
                :state.getEvent().eventId();

        try{
            var metadata=kafka.send(
                    TOPIC,
                    key,
                    state.getPayload()
            ).get(10,TimeUnit.SECONDS).getRecordMetadata();

            state.setPartition(metadata.partition());
            state.setOffset(metadata.offset());
        }catch(Exception e){
            throw new IllegalStateException(
                    "Cannot publish fixture event",
                    e
            );
        }
    }

    public void awaitConsumed(){
        if(state.getOffset()<0){
            return;
        }

        //Capture scenario values before Awaitility uses its worker thread.
        TopicPartition target=new TopicPartition(
                TOPIC,
                state.getPartition()
        );
        long required=state.getOffset()+1;

        Map<String,Object> properties=Map.of(
                "bootstrap.servers",broker.getBrokersAsString(),
                "default.api.timeout.ms",3000,
                "request.timeout.ms",3000
        );

        try(Admin admin=Admin.create(properties)){
            await()
                    .atMost(Duration.ofSeconds(25))
                    .pollInterval(Duration.ofMillis(200))
                    .until(()->{
                        var committed=admin
                                .listConsumerGroupOffsets(
                                        "homehub-notification-cucumber"
                                )
                                .partitionsToOffsetAndMetadata()
                                .get(3,TimeUnit.SECONDS)
                                .get(target);

                        return committed!=null
                                &&committed.offset()>=required;
                    });
        }
    }

    public void awaitDlt(){
        Map<String,Object> properties=Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "cucumber-dlt-"+UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        String expected=state.getPayload();

        DefaultKafkaConsumerFactory<String,String> factory=
                new DefaultKafkaConsumerFactory<>(
                        properties,
                        new StringDeserializer(),
                        new StringDeserializer()
                );

        try(Consumer<String,String> consumer=factory.createConsumer()){
            consumer.subscribe(List.of(DLT));

            await()
                    .atMost(Duration.ofSeconds(25))
                    .until(()->{
                        for(var record:consumer.poll(
                                Duration.ofMillis(200)
                        )){
                            if(expected.equals(record.value())){
                                return true;
                            }
                        }

                        return false;
                    });
        }

        awaitConsumed();
    }

    public void assertReceiptPublished(){
        Long count=jdbc.queryForObject(
                "select count(*) from outbox_event "
                        +"where event_key=? and published_at is not null",
                Long.class,
                state.getEvent().eventId()
        );

        if(count==null||count!=1){
            throw new AssertionError(
                    "Expected one published consumer receipt"
            );
        }
    }
}