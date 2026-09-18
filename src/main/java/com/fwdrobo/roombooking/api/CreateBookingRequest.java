package com.fwdrobo.roombooking.api;

import java.time.LocalDateTime;

public record CreateBookingRequest(LocalDateTime start, LocalDateTime end) {
}