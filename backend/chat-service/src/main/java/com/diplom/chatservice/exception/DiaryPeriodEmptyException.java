package com.diplom.chatservice.exception;

/** A period summary was requested but the period has no summarized days yet. */
public class DiaryPeriodEmptyException extends RuntimeException {
    public DiaryPeriodEmptyException(String message) {
        super(message);
    }
}
