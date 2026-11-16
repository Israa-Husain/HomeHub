package com.ga.HomeHub.booking.security;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;


//Validate bearer signatures in Booking while leaving token issuance to Auth.
@Service
public class JWTUtils {
    private final Logger logger=Logger.getLogger(JWTUtils.class.getName());
    @Value("${jwt-secret}")
    private String JwtSecret;

    public boolean validateJwtToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(JwtSecret)
                    .build()
                    .parseClaimsJws(token);

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
