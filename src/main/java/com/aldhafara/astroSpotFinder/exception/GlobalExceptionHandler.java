package com.aldhafara.astroSpotFinder.exception;

import com.aldhafara.astroSpotFinder.model.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now().toString(),
                400,
                "Bad Request",
                "Invalid parameter: " + ex.getName()
        ));
    }

    @ExceptionHandler({ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationException(ConstraintViolationException ex) {
        return ResponseEntity
                .badRequest().body(new ApiErrorResponse(
                        Instant.now().toString(),
                        400,
                        "Invalid request parameters",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                Instant.now().toString(),
                500,
                "Internal Server Error",
                ex.getMessage()
        ));
    }
}
