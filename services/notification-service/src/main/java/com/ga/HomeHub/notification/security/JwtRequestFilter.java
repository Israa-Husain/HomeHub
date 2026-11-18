package com.ga.HomeHub.notification.security;

import com.ga.HomeHub.notification.exception.ApiException;
import com.ga.HomeHub.notification.service.AccountVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtRequestFilter extends OncePerRequestFilter{
    private final AccountVerificationService accounts;
    private final ObjectMapper mapper=new ObjectMapper();

    public JwtRequestFilter(AccountVerificationService accounts){
        this.accounts=accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    )throws ServletException,IOException{
        String bearer=request.getHeader("Authorization");

        if(bearer!=null){
            try{
                MyUserDetails user=accounts.verify(bearer);

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_"+user.role()))
                        )
                );
            }catch(ApiException e){
                SecurityContextHolder.clearContext();

                response.setStatus(e.getStatus().value());
                response.setContentType("application/json");

                mapper.writeValue(
                        response.getOutputStream(),
                        Map.of(
                                "status",e.getStatus().value(),
                                "message",e.getMessage()
                        )
                );

                return;
            }
        }

        chain.doFilter(request,response);
    }
}