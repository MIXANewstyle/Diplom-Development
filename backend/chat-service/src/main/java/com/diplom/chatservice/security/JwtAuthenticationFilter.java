package com.diplom.chatservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (jwtService.isTokenValid(token)) {
            Claims claims = jwtService.extractAllClaims(token);
            
            String scope = claims.get("scope", String.class);
            if ("guest".equals(scope)) {
                UUID participantId = UUID.fromString(claims.getSubject());
                String aud = claims.getAudience();
                UUID roomId = UUID.fromString(aud.replace("room:", ""));
                
                GuestPrincipal guestPrincipal = new GuestPrincipal(participantId, roomId);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        guestPrincipal,
                        null,
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_GUEST"))
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                String email = claims.getSubject();
                UUID userId = UUID.fromString(claims.get("userId", String.class));
                String roleName = claims.get("role", String.class);

                CustomUserDetails userDetails = new CustomUserDetails(userId, email, roleName);
                
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_" + roleName))
                );
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
