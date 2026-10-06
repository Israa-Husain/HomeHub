package com.ga.HomeHub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.concurrent.*;

@Component
public class RateLimit extends OncePerRequestFilter {
    private record Counter(long start, int count) {
    }

    private final ConcurrentMap<String, Counter> map = new ConcurrentHashMap<>();

    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException{
        String path = req.getRequestURI();
        if(path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/forgot-password")){
            String key = req.getRemoteAddr() + path;
            long now = System.currentTimeMillis();
            Counter c = map.compute(key, (k, v) -> v == null || now - v.start() > 60000 ? new Counter(now, 1) : new Counter(v.start(), v.count() + 1));
            if(c.count() > 10){
                res.setStatus(429);
                res.getWriter().write("Too many requests");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}

