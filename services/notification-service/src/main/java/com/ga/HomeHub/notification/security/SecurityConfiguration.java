package com.ga.HomeHub.notification.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration{
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtRequestFilter jwt
    )throws Exception{
        http.csrf(csrf->csrf.disable())
                .cors(cors->{})
                .sessionManagement(session->session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR,
                                DispatcherType.ASYNC
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors->errors
                        .authenticationEntryPoint((request,response,e)->
                                response.sendError(401))
                        .accessDeniedHandler((request,response,e)->
                                response.sendError(403)));

        http.addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtRequestFilter> jwtRegistration(
            JwtRequestFilter filter
    ){
        FilterRegistrationBean<JwtRequestFilter> registration=
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}