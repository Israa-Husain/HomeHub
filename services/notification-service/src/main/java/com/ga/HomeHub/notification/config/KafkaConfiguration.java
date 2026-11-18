package com.ga.HomeHub.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration{
    @Bean
    public NewTopic notificationTopic(
            @Value("${homehub.events.topic}") String topic,
            @Value("${homehub.events.partitions:3}") int partitions,
            @Value("${homehub.events.replicas:1}") int replicas
    ){
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic(
            @Value("${homehub.events.dlt}") String topic,
            @Value("${homehub.events.partitions:3}") int partitions,
            @Value("${homehub.events.replicas:1}") int replicas
    ){
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String,String> kafka,
            @Value("${homehub.events.dlt}") String dlt
    ){
        DeadLetterPublishingRecoverer recoverer=
                new DeadLetterPublishingRecoverer(
                        kafka,
                        (record,error)->new TopicPartition(
                                dlt,
                                record.partition()
                        )
                );

        //Never skip the original event when dead-letter publication fails.
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler handler=new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(2000L,3L)
        );

        //Send invalid contracts directly to DLT. Retry transient database and SMTP failures.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);

        return handler;
    }
}