package com.ga.HomeHub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimit extends OncePerRequestFilter {
    private final Map<String, Integer> requestCounts = new HashMap<>();
    private final Map<String, Long> startTimes = new HashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Only rate limit selected public endpoints
        if(path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/forgot-password")){

            String key = request.getRemoteAddr() + path;
            long currentTime = System.currentTimeMillis();

            // First request or 60 seconds have passed
            if(!startTimes.containsKey(key) || currentTime - startTimes.get(key) > 60000){
                startTimes.put(key, currentTime);
                requestCounts.put(key, 1);
            } else{
                int count = requestCounts.get(key) + 1;
                requestCounts.put(key, count);

                // More than 10 requests in 60 seconds
                if(count > 10){
                    response.setStatus(429);
                    response.setContentType("application/json");
                    response.getWriter()
                            .write("{\"message\":\"Too many requests\"}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
