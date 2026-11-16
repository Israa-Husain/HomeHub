package com.ga.HomeHub.booking.config;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;



@Configuration
public class BookingConfiguration {
    @Bean
    public OpenAPI homeHubOpenAPI() {
        String bearer = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("HomeHub Booking API")
                        .version("4.0.0")
                        .description("Home services, provider schedules and booking workflows"))
                .components(new Components().addSecuritySchemes(bearer,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(bearer));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${homehub.cors.origins:http://localhost:5173}") String origins){
        CorsConfiguration config=new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins.split(",")));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config);return source;
    }

    //Enable scheduled jobs unless homehub.scheduling.enabled is false.
    @Configuration
    @org.springframework.scheduling.annotation.EnableScheduling
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="homehub.scheduling.enabled",havingValue="true",matchIfMissing=true)
    public static class SchedulingConfiguration{}
}
