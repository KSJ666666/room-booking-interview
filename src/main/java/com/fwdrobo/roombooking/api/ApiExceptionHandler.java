package com.fwdrobo.roombooking.api;

import com.fwdrobo.roombooking.service.BookingConflictException;
import com.fwdrobo.roombooking.service.BookingNotFoundException;
import com.fwdrobo.roombooking.service.InvalidBookingWindowException;
import com.fwdrobo.roombooking.service.RoomNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ApiError> handleRoomNotFound(
            RoomNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiError> handleBookingNotFound(
            BookingNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "BOOKING_NOT_FOUND",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidBookingWindowException.class)
    public ResponseEntity<ApiError> handleInvalidWindow(
            InvalidBookingWindowException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_WINDOW", exception.getMessage(), request);
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ApiError> handleBookingConflict(
            BookingConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "BOOKING_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error",
                request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
