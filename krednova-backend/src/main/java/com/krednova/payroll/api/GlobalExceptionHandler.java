package com.krednova.payroll.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error shape for the whole API: { "message": "..." }. Internal details are never returned. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    public record Err(String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Err> api(ApiException e) { return ResponseEntity.status(e.status()).body(new Err(e.getMessage())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Err> invalid(MethodArgumentNotValidException e) {
        String m = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new Err(m));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Err> unreadable(HttpMessageNotReadableException e) { return ResponseEntity.badRequest().body(new Err("Malformed request body.")); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Err> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(new Err("That record already exists or conflicts with existing data.")); }
}
