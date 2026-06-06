package com.diplom.chatservice.dto.ws;

public record AiResponseEvent(
        String type,
        AssistantTurnDto assistantTurn
) {
    public static AiResponseEvent of(AssistantTurnDto assistantTurn) {
        return new AiResponseEvent("AI_RESPONSE", assistantTurn);
    }
}
