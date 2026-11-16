package com.ga.HomeHub.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimit extends OncePerRequestFilter{
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();

    private record Window(long start,int count){}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI();
        if(path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/forgot-password")){
            long now=System.currentTimeMillis();

            //Remove expired entries when the tracked address count grows.
            if(windows.size()>10000){
                windows.entrySet().removeIf(entry->now-entry.getValue().start()>60000);
            }

            //Reject requests when the local tracking limit is exceeded.
            if(windows.size()>20000){
                response.sendError(429);
                return;
            }

            Window window=windows.compute(request.getRemoteAddr()+path, (key,old)->old==null || now-old.start()>60000 ?new Window(now,1) :new Window(old.start(),old.count()+1));

            if(window.count()>10){
                response.sendError(429);
                return;
            }
        }
        chain.doFilter(request,response);
    }
}