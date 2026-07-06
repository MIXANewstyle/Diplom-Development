package com.diplom.chatservice.dto.ws;

/**
 * Structured error payload sent to the caller's user queue
 * ({@code /user/queue/errors}).
 *
 * <ul>
 *   <li>{@code type = "LIMIT"} — draft text length or buffer count exceeded.</li>
 *   <li>{@code type = "ERROR"} — wrong state, not your turn, etc.</li>
 *   <li>{@code type = "SEQ_MISMATCH"} — client turnSeq != server expected seq.</li>
 * </ul>
 */
public record WsError(
    String type,
    String errorType,
    String message,
    Long expectedTurnSeq
) {
    public static WsError limit(String message) {
        return new WsError("WS_ERROR", "LIMIT", message, null);
    }

    public static WsError error(String message) {
        return new WsError("WS_ERROR", "ERROR", message, null);
    }

    public static WsError seqMismatch(long expectedTurnSeq) {
        return new WsError("WS_ERROR", "SEQ_MISMATCH",
                "Sequence mismatch: expected turnSeq=" + expectedTurnSeq,
                expectedTurnSeq);
    }
}

