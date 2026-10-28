package com.ga.HomeHub;

import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.repository.UserRepository;
import com.ga.HomeHub.security.JWTUtils;
import com.ga.HomeHub.security.JwtRequestFilter;
import com.ga.HomeHub.security.MyUserDetails;
import com.ga.HomeHub.security.MyUserDetailsService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

//Real JWT signing/filter logic with mocked persistence; no live account or production signing key.
@ExtendWith(MockitoExtension.class)
class JwtAccountSecurityTest{
    @Mock
    private UserRepository users;
    @Mock
    private FilterChain chain;
    private JWTUtils jwt;
    private JwtRequestFilter filter;
    private Key signingKey;
    private static final String EMAIL="security-test@homehub.example";

    @BeforeEach
    void setup(){
        SecurityContextHolder.clearContext();
        signingKey=Keys.secretKeyFor(SignatureAlgorithm.HS256);
        jwt=new JWTUtils();
        ReflectionTestUtils.setField(jwt,"JwtSecret",Encoders.BASE64.encode(signingKey.getEncoded()));
        ReflectionTestUtils.setField(jwt,"JwtExpiration",60000);
        filter=new JwtRequestFilter();
        ReflectionTestUtils.setField(filter,"jwtUtils",jwt);
        ReflectionTestUtils.setField(filter,"myUserDetailsService",new MyUserDetailsService(users));
    }

    @AfterEach
    void cleanup(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void signedTokensLoadCurrentAccountRoleForAllThreeRoles() throws Exception{
        for(Role role:Role.values()){
            SecurityContextHolder.clearContext();
            User user=account(role);
            when(users.findUserByEmailAddress(EMAIL)).thenReturn(user);
            filter(jwt.generateJwtToken(new MyUserDetails(user)));
            var authentication=SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication);
            assertEquals(EMAIL,authentication.getName());
            assertTrue(authentication.getAuthorities().stream()
                    .anyMatch(authority->authority.getAuthority().equals("ROLE_"+role.name())));
        }
        verify(users,times(Role.values().length)).findUserByEmailAddress(EMAIL);
        verify(chain,times(Role.values().length)).doFilter(any(),any());
    }

    @Test
    void existingValidTokenStopsAuthenticatingAfterAccountDeactivation() throws Exception{
        User user=account(Role.HOMEOWNER);
        when(users.findUserByEmailAddress(EMAIL)).thenReturn(user);
        String token=jwt.generateJwtToken(new MyUserDetails(user));
        filter(token);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        user.setStatus(UserStatus.INACTIVE);
        assertTrue(jwt.validateJwtToken(token));
        filter(token);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(users,times(2)).findUserByEmailAddress(EMAIL);
        verify(chain,times(2)).doFilter(any(),any());
    }

    @Test
    void expiredSignedTokenDoesNotLoadAnAccount() throws Exception{
        String expired=Jwts.builder().setSubject(EMAIL)
                .setIssuedAt(new Date(System.currentTimeMillis()-7200000))
                .setExpiration(new Date(System.currentTimeMillis()-3600000))
                .signWith(signingKey).compact();
        filter(expired);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
        verify(chain).doFilter(any(),any());
    }

    @Test
    void malformedAndMissingTokensDoNotLoadAnAccount() throws Exception{
        filter("invalid-test-token");
        filter(null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
        verify(chain,times(2)).doFilter(any(),any());
    }

    @Test
    void tokenSignedWithAnotherKeyDoesNotLoadAnAccount() throws Exception{
        String invalid=Jwts.builder().setSubject(EMAIL).setExpiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact();
        filter(invalid);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
        verify(chain).doFilter(any(),any());
    }

    private User account(Role role){
        User user=new User();
        user.setId(1L);
        user.setEmailAddress(EMAIL);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private void filter(String token) throws Exception{
        MockHttpServletRequest request=new MockHttpServletRequest();
        if(token!=null){
            request.addHeader("Authorization","Bearer "+token);
        }
        filter.doFilter(request,new MockHttpServletResponse(),chain);
    }
}
