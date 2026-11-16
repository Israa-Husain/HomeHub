package com.ga.HomeHub.notification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class ApiExceptionHandler{
    private static final Logger logger=
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    private ResponseEntity<ApiError> out(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ){
        return ResponseEntity.status(status).body(
                new ApiError(
                        LocalDateTime.now(),
                        status.value(),
                        status.name(),
                        message,
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> application(
            ApiException e,
            HttpServletRequest request
    ){
        return out(e.getStatus(),e.getMessage(),request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> badRequest(
            Exception e,
            HttpServletRequest request
    ){
        return out(HttpStatus.BAD_REQUEST,"Invalid request",request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(
            Exception e,
            HttpServletRequest request
    ){
        return out(HttpStatus.FORBIDDEN,"Access denied",request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(
            Exception e,
            HttpServletRequest request
    ){
        return out(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> missing(
            Exception e,
            HttpServletRequest request
    ){
        return out(HttpStatus.NOT_FOUND,"Resource not found",request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> database(
            Exception e,
            HttpServletRequest request
    ){
        logger.error("Notification database request failed",e);

        return out(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Notification storage unavailable",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(
            Exception e,
            HttpServletRequest request
    ){
        logger.error("Unexpected notification request failure",e);

        return out(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }
}