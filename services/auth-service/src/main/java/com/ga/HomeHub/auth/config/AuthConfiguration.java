package com.ga.HomeHub.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Configuration
public class AuthConfiguration implements WebMvcConfigurer{

    //Document bearer authentication and internal service credentials.
    @Bean
    public OpenAPI homeHubOpenAPI(){
        return new OpenAPI()
                .info(new Info().title("HomeHub Auth API").version("4.0.0").description("Accounts, authentication and private account APIs"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("internalKey",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Internal-Key")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    //Configure browser access using environment-owned frontend origins.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${homehub.cors.origins:http://localhost:5173}") String origins){

        CorsConfiguration config=new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins.split(",")));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID"));

        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config);
        return source;
    }

    //Create the S3 client only when a bucket is configured.
    @Bean(destroyMethod="close")
    @ConditionalOnExpression("!'${homehub.storage.bucket:}'.isEmpty()")
    public S3Client profileImageS3Client(@Value("${homehub.storage.public-base:}") String publicBase){
        if(publicBase.isBlank() || !publicBase.startsWith("https://")){
            throw new IllegalStateException("Configure an HTTPS profile-image delivery base URL");
        }
        return S3Client.create();
    }

    //Serve locally stored profile images through their returned /uploads URL.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        String uploadPath=Paths.get("uploads").toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadPath);
    }

    //Keep scheduling independently switchable without disabling other configuration.
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="homehub.scheduling.enabled", havingValue="true", matchIfMissing=true)
    public static class SchedulingConfiguration{}
}