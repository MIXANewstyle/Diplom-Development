package com.diplom.contentservice.exception;

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

    @ExceptionHandler(TagNotFoundException.class)
    public ResponseEntity<Object> handleTagNotFoundException(TagNotFoundException ex) {
        log.warn("TagNotFoundException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<Object> handlePostNotFoundException(PostNotFoundException ex) {
        log.warn("PostNotFoundException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TagAlreadyExistsException.class)
    public ResponseEntity<Object> handleTagAlreadyExistsException(TagAlreadyExistsException ex) {
        log.warn("TagAlreadyExistsException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TagInUseException.class)
    public ResponseEntity<Object> handleTagInUseException(TagInUseException ex) {
        log.warn("TagInUseException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NotPostAuthorException.class)
    public ResponseEntity<Object> handleNotPostAuthorException(NotPostAuthorException ex) {
        log.warn("NotPostAuthorException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidPostStateException.class)
    public ResponseEntity<Object> handleInvalidPostStateException(InvalidPostStateException ex) {
        log.warn("InvalidPostStateException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidPublicationException.class)
    public ResponseEntity<Object> handleInvalidPublicationException(InvalidPublicationException ex) {
        log.warn("InvalidPublicationException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(InvalidTagReferenceException.class)
    public ResponseEntity<Object> handleInvalidTagReferenceException(InvalidTagReferenceException ex) {
        log.warn("InvalidTagReferenceException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<Object> handleCommentNotFoundException(CommentNotFoundException ex) {
        log.warn("CommentNotFoundException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NotCommentAuthorException.class)
    public ResponseEntity<Object> handleNotCommentAuthorException(NotCommentAuthorException ex) {
        log.warn("NotCommentAuthorException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidCommentStateException.class)
    public ResponseEntity<Object> handleInvalidCommentStateException(InvalidCommentStateException ex) {
        log.warn("InvalidCommentStateException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CommentEditWindowExpiredException.class)
    public ResponseEntity<Object> handleCommentEditWindowExpiredException(CommentEditWindowExpiredException ex) {
        log.warn("CommentEditWindowExpiredException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ReplyToReplyException.class)
    public ResponseEntity<Object> handleReplyToReplyException(ReplyToReplyException ex) {
        log.warn("ReplyToReplyException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(InvalidCommentParentException.class)
    public ResponseEntity<Object> handleInvalidCommentParentException(InvalidCommentParentException ex) {
        log.warn("InvalidCommentParentException: {}", ex.getMessage());
        Map<String, Object> body = createBody(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex) {
        log.error("Unknown exception occurred: ", ex);
        Map<String, Object> body = createBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
