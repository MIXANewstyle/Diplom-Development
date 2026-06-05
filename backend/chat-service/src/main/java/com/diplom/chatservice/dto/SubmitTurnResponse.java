package com.diplom.chatservice.dto;

public record SubmitTurnResponse(
    TurnResponse userTurn,
    TurnResponse assistantTurn
) {}
