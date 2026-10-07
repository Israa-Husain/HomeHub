package com.ga.HomeHub.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {
    private ResponseEntity<ApiError> out(HttpStatus status, Exception e, HttpServletRequest r){
        return ResponseEntity.status(status).body(new ApiError(LocalDateTime.now(),status.value(),status.name(),e.getMessage(),r.getRequestURI()));
    }

    @ExceptionHandler(InformationNotFoundException.class)
    ResponseEntity<ApiError> notFound(Exception e, HttpServletRequest r){
        return out(HttpStatus.NOT_FOUND,e,r);
    }

    @ExceptionHandler(InformationExistException.class)
    ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest r){
        return out(HttpStatus.CONFLICT,e,r);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> forbidden(Exception e, HttpServletRequest r) {
        return out(HttpStatus.FORBIDDEN, e, r);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> unauthorized(Exception e, HttpServletRequest r) {
        return out(HttpStatus.UNAUTHORIZED, e, r);
    }

    @ExceptionHandler({BookingConflictException.class, InvalidBookingStatusException.class})
    ResponseEntity<ApiError> booking(Exception e, HttpServletRequest r) {
        return out(HttpStatus.UNPROCESSABLE_ENTITY, e, r);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest r) {
        return out(HttpStatus.BAD_REQUEST, e, r);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");

        return out(HttpStatus.BAD_REQUEST,
                new IllegalArgumentException(message),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internalError(
            Exception e,
            HttpServletRequest request) {

        return out(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new RuntimeException("An unexpected error occurred"),
                request
        );
    }


}
