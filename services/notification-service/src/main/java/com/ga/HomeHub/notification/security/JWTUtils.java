package com.ga.HomeHub.notification.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JWTUtils{
    private final String secret;

    public JWTUtils(@Value("${jwt-secret}") String secret){
        this.secret=secret;
    }

    public String subject(String token){
        //Use the same Base64 signing-key interpretation as the existing Auth JWT utility.
        String subject=Jwts.parserBuilder()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        if(subject==null || subject.isBlank()){
            throw new IllegalArgumentException("Invalid token subject");
        }

        return subject;
    }
}