package com.diplom.chatservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private Map<String, Object> createBody(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return body;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> body = createBody(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed");
        
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        body.put("fieldErrors", fieldErrors);
        
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = createBody(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed JSON request");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("DataIntegrityViolationException: ", ex);
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", "Operation conflicts with existing data");
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> body = createBody(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<Object> handleRoomNotFoundException(RoomNotFoundException ex) {
        log.warn("RoomNotFoundException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NotRoomParticipantException.class)
    public ResponseEntity<Object> handleNotRoomParticipantException(NotRoomParticipantException ex) {
        log.warn("NotRoomParticipantException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<Object> handleRoomFullException(RoomFullException ex) {
        log.warn("RoomFullException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidRoomStateException.class)
    public ResponseEntity<Object> handleInvalidRoomStateException(InvalidRoomStateException ex) {
        log.warn("InvalidRoomStateException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotFriendsException.class)
    public ResponseEntity<Object> handleNotFriendsException(NotFriendsException ex) {
        log.warn("NotFriendsException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex) {
        log.error("Unknown exception occurred: ", ex);
        Map<String, Object> body = createBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<Object> handleLlmUnavailableException(LlmUnavailableException ex) {
        log.error("LlmUnavailableException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "The LLM provider is currently unavailable. Please try again later.");
        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
