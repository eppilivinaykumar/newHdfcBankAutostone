package com.mybank.autostone.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Central error mapping for every REST endpoint.
 *
 * Every branch now logs the real exception server-side (previously only the generic
 * catch-all logged nothing at all, so a NullPointerException or any other unexpected
 * failure while starting a JOB/BOX surfaced only as an opaque "Request failed" with
 * nothing in the application log to explain why). The response body still never leaks
 * a full stack trace to the browser, but it now includes the exception type and message
 * so the AutoStone UI can show something actionable instead of a dead end.
 */
@Slf4j
@ControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> notFound(Exception e) {
        log.warn("404 Not Found: {}", e.getMessage());
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> conflict(Exception e) {
        log.warn("409 Conflict (optimistic lock): {}", e.getMessage());
        return body(HttpStatus.CONFLICT, "Execution changed concurrently; retry the operation");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> bad(Exception e) {
        log.warn("400 Bad Request: {}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Catch-all safety net. This used to swallow the exception completely and return the
     * fixed string "Request failed", which is why a null-pointer or any other bug while
     * starting a JOB/BOX looked identical to every other failure with no way to tell them
     * apart from the UI or the server console. Now it is always logged with the full stack
     * trace, and the response carries the exception type + message for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> generic(Exception e) {
        log.error("500 Internal Server Error while handling request", e);
        String detail = e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        return body(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    private ResponseEntity<?> body(HttpStatus s, String m) {
        return ResponseEntity.status(s).body(Map.of(
                "timestamp", LocalDateTime.now(),
                "status", s.value(),
                "message", m == null ? "Request failed" : m
        ));
    }
}
