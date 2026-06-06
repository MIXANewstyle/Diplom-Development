package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ws.WsError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Helper that sends structured error payloads to a caller's user queue
 * ({@code /user/queue/errors}). Used by draft and presence handlers.
 *
 * <p>Per §16: WS errors are ERROR frames on the user queue — never HTTP status codes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsErrorSender {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send a structured error to the user's personal error queue.
     *
     * @param principalName the principal name (email) resolved from the STOMP session
     * @param error         the structured error payload
     */
    public void send(String principalName, WsError error) {
        log.debug("Sending WS error to user={}: type={}, message={}",
                principalName, error.errorType(), error.message());
        messagingTemplate.convertAndSendToUser(principalName, "/queue/errors", error);
    }
}
