package org.example.mendel_challenge.common;

import lombok.extern.slf4j.Slf4j;
import org.example.mendel_challenge.transaction.exceptions.TransactionAlreadyExistsException;
import org.example.mendel_challenge.transaction.exceptions.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 409 - id already exists
    @ExceptionHandler(TransactionAlreadyExistsException.class)
    public ResponseEntity<String> handleDuplicateTransaction(TransactionAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // 400 - missing or invalid fields (triggered by @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        return ResponseEntity.badRequest().body(errors);
    }

    // 400 - malformed JSON body (e.g. wrong types, missing braces)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body("Malformed JSON request body.");
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<String> handleMissingTransaction(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

}

