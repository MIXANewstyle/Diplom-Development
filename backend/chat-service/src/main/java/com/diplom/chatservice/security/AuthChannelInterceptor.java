package com.diplom.chatservice.security;

import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.service.ModerationBlocklistService;
import com.diplom.chatservice.service.WsSessionRegistry;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound STOMP channel interceptor handling:
 * <ul>
 *   <li><b>CONNECT</b> — JWT authentication (same principal type as the HTTP filter).</li>
 *   <li><b>SUBSCRIBE</b> — room-participant authorization for room destinations;
 *       {@code /user/**} subscriptions pass through (Spring scopes them to the
 *       authenticated principal automatically).</li>
 *   <li><b>SEND</b> — room-participant authorization for {@code /app/rooms/{roomId}/**}
 *       destinations (§9.2 enforces authorization at the broker boundary for SEND too).</li>
 * </ul>
 *
 * <p>This replaces Spring Security's deprecated message-level security config.
 * Invalid/missing tokens or unauthorized commands cause a {@link MessageDeliveryException}
 * which Spring translates to a STOMP ERROR frame.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ModerationBlocklistService moderationBlocklistService;
    private final WsSessionRegistry wsSessionRegistry;

    /**
     * Matches destinations like:
     * <ul>
     *   <li>/topic/rooms/{roomId}</li>
     *   <li>/topic/rooms/{roomId}/...</li>
     *   <li>/app/rooms/{roomId}/...</li>
     * </ul>
     */
    private static final Pattern ROOM_DESTINATION_PATTERN =
            Pattern.compile("^/(topic|app)/rooms/([0-9a-fA-F\\-]{36})(/.*)?$");

    /** Matches /user/** destinations — these are always allowed for authenticated users. */
    private static final Pattern USER_DESTINATION_PATTERN =
            Pattern.compile("^/user/.*$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> { /* pass through */ }
        }

        return message;
    }

    /**
     * Authenticate the STOMP CONNECT frame using the JWT from the "Authorization" native header.
     * Builds the same {@link UsernamePasswordAuthenticationToken} with {@link CustomUserDetails}
     * principal that the HTTP {@link JwtAuthenticationFilter} produces.
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException("Invalid or expired JWT token");
        }

        Claims claims = jwtService.extractAllClaims(token);
        String scope = claims.get("scope", String.class);
        
        UsernamePasswordAuthenticationToken authentication;
        
        if ("guest".equals(scope)) {
            UUID participantId = UUID.fromString(claims.getSubject());
            String aud = claims.getAudience();
            UUID roomId = UUID.fromString(aud.replace("room:", ""));
            
            GuestPrincipal guestPrincipal = new GuestPrincipal(participantId, roomId);
            authentication = new UsernamePasswordAuthenticationToken(
                    guestPrincipal,
                    null,
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_GUEST"))
            );
            
            log.debug("STOMP CONNECT authenticated for guest participantId={}", participantId);
        } else {
            String email = claims.getSubject();
            UUID userId = UUID.fromString(claims.get("userId", String.class));
            String roleName = claims.get("role", String.class);

            CustomUserDetails userDetails = new CustomUserDetails(userId, email, roleName);

            if (moderationBlocklistService.isBlocked(userId)) {
                throw new MessageDeliveryException("Account is moderated");
            }

            authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_" + roleName))
            );
            
            wsSessionRegistry.registerUserId(userId, accessor.getSessionId());
            log.debug("STOMP CONNECT authenticated for userId={}", userId);
        }

        accessor.setUser(authentication);

        // Store raw JWT for downstream handlers (e.g. room-state snapshot enrichment)
        accessor.getSessionAttributes().put("jwt", token);
    }

    /**
     * Authorize SUBSCRIBE to room destinations. Extracts roomId from the destination path
     * and verifies the connected user is a participant. {@code /user/**} subscriptions
     * pass through (Spring scopes them to the authenticated principal automatically).
     * Non-room, non-user destinations pass through.
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        checkNotBlocked(accessor);

        // /user/** subscriptions pass through — Spring scopes to the authenticated principal
        if (USER_DESTINATION_PATTERN.matcher(destination).matches()) {
            log.debug("SUBSCRIBE pass-through for user destination: {}", destination);
            return;
        }

        Matcher matcher = ROOM_DESTINATION_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            // Not a room destination — pass through unchanged
            return;
        }

        verifyRoomParticipant(accessor, destination, matcher, "SUBSCRIBE");
    }

    /**
     * Authorize SEND to {@code /app/rooms/{roomId}/**}. Extracts roomId and verifies the
     * sender is a participant of the room (§9.2 enforces authorization at the broker
     * boundary for SEND too).
     */
    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        checkNotBlocked(accessor);

        Matcher matcher = ROOM_DESTINATION_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            // Not a room destination — pass through
            return;
        }

        // Only authorize /app/ destinations (the inbound app prefix)
        String prefix = matcher.group(1);
        if (!"app".equals(prefix)) {
            return;
        }

        verifyRoomParticipant(accessor, destination, matcher, "SEND");
    }

    /**
     * Common participant-verification logic shared between SUBSCRIBE and SEND handlers.
     */
    private void verifyRoomParticipant(StompHeaderAccessor accessor, String destination,
                                       Matcher matcher, String command) {
        UUID roomId;
        try {
            roomId = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new MessageDeliveryException("Invalid room ID in destination: " + destination);
        }

        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        Object principal = auth.getPrincipal();

        boolean isParticipant = false;

        if (principal instanceof GuestPrincipal guest) {
            if (guest.getRoomId().equals(roomId)) {
                isParticipant = roomParticipantRepository.existsByIdAndRoomId(guest.getParticipantId(), roomId);
            }
        } else if (principal instanceof CustomUserDetails user) {
            isParticipant = roomParticipantRepository.existsByRoomIdAndUserId(roomId, user.getId());
        }

        if (!isParticipant) {
            log.warn("{} rejected: principal is not a participant of roomId={}", command, roomId);
            throw new MessageDeliveryException(
                    "Access denied: you are not a participant of this room");
        }

        log.debug("{} authorized: principal to destination={}", command, destination);
    }

    private void checkNotBlocked(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) accessor.getUser();
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                if (moderationBlocklistService.isBlocked(userDetails.getId())) {
                    throw new MessageDeliveryException("Account is moderated");
                }
            }
        }
    }
}
