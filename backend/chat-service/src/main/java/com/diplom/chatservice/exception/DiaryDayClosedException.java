package com.diplom.chatservice.exception;

/** The requested diary date is outside the writable window (today ± 1 day). */
public class DiaryDayClosedException extends RuntimeException {
    public DiaryDayClosedException(String message) {
        super(message);
    }
}
