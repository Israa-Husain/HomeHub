package com.ga.HomeHub.security;

/*
1. JWT utility
-Methods: generateJWTToken, getUserNameFromJWTToken, validatejwtToken
2.JwtRequestFilter:
-Methods: parseJwt, doFilterInternal
3. JwtRequestFilter -> SecurityConfiguration
* */

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class JWTUtils {

    Logger logger = Logger.getLogger(JWTUtils.class.getName());

    @Value("${jwt-secret}")
    private String JwtSecret;

    @Value("${jwt-expiration-ms}")
    private int JwtExpiration;

    public String generateJwtToken(MyUserDetails myUserDetails) {
        return Jwts.builder()
                .setSubject(myUserDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + JwtExpiration))
                .signWith(SignatureAlgorithm.HS256, JwtSecret)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(JwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(JwtSecret)
                    .build()
                    .parseClaimsJws(authToken);

            return true;

        } catch (Exception e) {
            logger.log(
                    Level.SEVERE,
                    "Invalid JWT Token: {0}",
                    e.getMessage()
            );
        }

        return false;
    }

}
