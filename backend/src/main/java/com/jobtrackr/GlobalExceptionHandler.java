package com.jobtrackr;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
       String message = ex.getBindingResult().getFieldError().getDefaultMessage();
       ApiError error = new ApiError(400, message);
       return ResponseEntity.badRequest().body(error);
    }
}
