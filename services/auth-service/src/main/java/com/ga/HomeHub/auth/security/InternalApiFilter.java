package com.ga.HomeHub.auth.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
//Require a service credential for internal lookups. Current-account resolution still requires a JWT.
public class InternalApiFilter extends OncePerRequestFilter{
    @Value("${homehub.internal-key}") private String key;
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(request.getRequestURI().startsWith("/internal/") && !request.getRequestURI().equals("/internal/users/current")){
            String supplied=request.getHeader("X-Internal-Key");
            if(key.length()<32 || supplied==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){response.sendError(401);return;}
        }
        chain.doFilter(request,response);
    }
}
