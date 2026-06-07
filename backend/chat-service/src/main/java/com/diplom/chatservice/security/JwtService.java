package com.diplom.chatservice.security;

import com.diplom.chatservice.config.ChatGuestProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    private final ChatGuestProperties guestProperties;

    public JwtService(ChatGuestProperties guestProperties) {
        this.guestProperties = guestProperties;
    }

    private Key getSigninKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigninKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String mintRoomScopedToken(UUID participantId, UUID roomId) {
        long expirationMillis = guestProperties.tokenTtl().toMillis();
        
        return Jwts.builder()
                .setClaims(Map.of("scope", "guest"))
                .setSubject(participantId.toString())
                .setAudience("room:" + roomId.toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigninKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
