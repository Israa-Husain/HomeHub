package com.ga.HomeHub.booking.security;

import com.ga.HomeHub.booking.client.AuthServiceClient;
import com.ga.HomeHub.booking.client.AccountLookupService;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {
    private final JWTUtils jwt;
    private final AuthServiceClient auth;
    private final AccountLookupService accounts;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            if (!jwt.validateJwtToken(bearer.substring(7))) {
                response.sendError(401);
                return;
            }
            try {
                User user = accounts.resolve(auth.current(bearer));
                if (user.getStatus() != UserStatus.ACTIVE) {
                    response.sendError(401);
                    return;
                }
                MyUserDetails details = new MyUserDetails(user);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            } catch (com.ga.HomeHub.booking.exception.UnauthorizedException e) {
                response.sendError(401);
                return;
            } catch (feign.FeignException.Unauthorized | feign.FeignException.Forbidden e) {
                response.sendError(401);
                return;
            } catch (Exception e) {
                response.sendError(503, "Account verification unavailable");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
