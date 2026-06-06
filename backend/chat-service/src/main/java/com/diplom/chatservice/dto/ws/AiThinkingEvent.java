package com.diplom.chatservice.dto.ws;

public record AiThinkingEvent(
        String type,
        UserTurnDto userTurn
) {
    public static AiThinkingEvent of(UserTurnDto userTurn) {
        return new AiThinkingEvent("AI_THINKING", userTurn);
    }
}
