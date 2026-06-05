package com.diplom.chatservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class TokenExtractor {

    public String currentBearerHeader() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest req = attrs.getRequest();
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalStateException("No bearer token in current request");
        }
        return auth;  // "Bearer <token>" — pass through as-is
    }
}
