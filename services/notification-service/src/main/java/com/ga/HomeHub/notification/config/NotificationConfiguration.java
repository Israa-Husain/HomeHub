package com.ga.HomeHub.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableScheduling
public class NotificationConfiguration{
    @Bean
    public OpenAPI homeHubOpenAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("HomeHub Notification API")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${homehub.cors.origins:http://localhost:5173}") String origins
    ){
        CorsConfiguration cors=new CorsConfiguration();

        cors.setAllowedOrigins(
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .toList()
        );

        cors.setAllowedMethods(List.of("GET","PATCH","OPTIONS"));

        cors.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Last-Event-ID",
                "X-Request-ID"
        ));

        cors.setExposedHeaders(List.of("X-Request-ID"));

        UrlBasedCorsConfigurationSource source=
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**",cors);

        return source;
    }

    @Bean(name="homeHubExecutor")
    public ThreadPoolTaskExecutor homeHubExecutor(){
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("homehub-notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;
    }
}