package com.diplom.chatservice.config;

import com.diplom.chatservice.security.AuthChannelInterceptor;
import com.diplom.chatservice.service.WsSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

/**
 * WebSocket / STOMP configuration for real-time chat transport.
 *
 * <p>Destination conventions (reused by later phases):
 * <ul>
 *   <li>{@code /topic/rooms/{roomId}} — client subscribes for ongoing room broadcasts
 *       (presence, consent, drafts, AI responses, turn changes, end/archive).</li>
 *   <li>{@code /app/rooms/{roomId}/state} — client subscribes for a one-shot state snapshot
 *       (returns {@code RoomStateSnapshot} only to the subscribing client).</li>
 *   <li>{@code /app/...} — client → server inbound app destinations (used from phase 3c onward:
 *       consent, draft, finish-thought, propose-end, end-response).</li>
 *   <li>{@code /user/queue/rooms/{roomId}} — per-participant targeted messages (e.g. "you hold the floor").</li>
 * </ul>
 *
 * <p>Uses the in-memory simple broker (single instance). Redis pub/sub fan-out for
 * horizontal scaling is a later phase.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;
    private final WsSessionRegistry wsSessionRegistry;

    @Bean
    public ThreadPoolTaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("ws-heartbeat-");
        return s;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO: restrict allowed origins before production deployment
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(wsHeartbeatScheduler())
                .setHeartbeatValue(new long[]{10000, 10000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(WebSocketHandler handler) {
                return new WebSocketHandlerDecorator(handler) {
                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        wsSessionRegistry.registerWebSocketSession(session.getId(), session);
                        super.afterConnectionEstablished(session);
                    }

                    @Override
                    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                        wsSessionRegistry.unregisterWebSocketSession(session.getId());
                        super.afterConnectionClosed(session, closeStatus);
                    }
                };
            }
        });
    }
}
