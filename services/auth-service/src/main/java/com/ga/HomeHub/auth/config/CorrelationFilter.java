package com.ga.HomeHub.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationFilter extends OncePerRequestFilter{

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)throws ServletException,IOException{
        String id=request.getHeader("X-Request-ID");

        //Accept only bounded request identifiers or generate a new one.
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,64}")){
            id=UUID.randomUUID().toString();
        }

        MDC.put("requestId",id);
        response.setHeader("X-Request-ID",id);

        //Clear logging context before the servlet thread handles another request.
        try{
            chain.doFilter(request,response);
        }finally{
            MDC.remove("requestId");
        }
    }
}