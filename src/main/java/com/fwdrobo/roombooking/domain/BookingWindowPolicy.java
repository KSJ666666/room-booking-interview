package com.fwdrobo.roombooking.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

@Component
public class BookingWindowPolicy {

    public BookingWindowResult evaluate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null){
            return BookingWindowResult.MISSING_BOUNDARY;
        }
        if (start.isAfter(end) || start.isEqual(end)){
            return BookingWindowResult.END_NOT_AFTER_START;
        }
        long duration = ChronoUnit.MINUTES.between(start, end);
        if (duration < 30 || duration > 120){
            return BookingWindowResult.DURATION_OUT_OF_RANGE;
        }
        return BookingWindowResult.VALID;

    }
}
