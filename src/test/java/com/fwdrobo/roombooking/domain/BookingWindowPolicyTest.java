package com.fwdrobo.roombooking.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author ksj
 */

public class BookingWindowPolicyTest {

    private final BookingWindowPolicy bookingWindowPolicy  = new BookingWindowPolicy();


    @Test
    public void returnsValidForExact30Minutes() {//1.时长通过为边界30
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T09:30:00");
        assertEquals(BookingWindowResult.VALID, bookingWindowPolicy.evaluate(start, end));

    }
    @Test
    public void returnsValidForExactlyOneHundredTwentyMinutes() {//2.时长通过为边界120
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T11:00:00");
        assertEquals(BookingWindowResult.VALID, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsDurationOutOfRangeForTwentyNineMinutes() {//3.上边界相邻为29
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T09:29:00");
        assertEquals(BookingWindowResult.DURATION_OUT_OF_RANGE, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsDurationOutOfRangeForOneHundredTwentyOneMinutes() {//4.下边界相邻为121
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T11:01:00");
        assertEquals(BookingWindowResult.DURATION_OUT_OF_RANGE, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsMissingBoundaryWhenStartIsNull(){//5.start缺失
        LocalDateTime start = null;
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T11:00:00");
        assertEquals(BookingWindowResult.MISSING_BOUNDARY, bookingWindowPolicy.evaluate(start, end));

    }
    @Test
    public void returnsMissingBoundaryWhenEndIsNull(){//6.end缺失
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = null;
        assertEquals(BookingWindowResult.MISSING_BOUNDARY, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsInvalidWhenStartIsAfterEnd(){//7.start在end之后
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:10:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T09:00:00");
        assertEquals(BookingWindowResult.END_NOT_AFTER_START, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsInvalidWhenStartIsSameAsEnd(){//8.start等于end
        LocalDateTime start = LocalDateTime.parse("2030-01-15T09:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T09:00:00");
        assertEquals(BookingWindowResult.END_NOT_AFTER_START, bookingWindowPolicy.evaluate(start, end));
    }
    @Test
    public void returnsEndNotAfterStartWhenMultipleRulesViolated(){ //9.多个规则同时违反
        LocalDateTime start = LocalDateTime.parse("2030-01-15T11:00:00");
        LocalDateTime end   = LocalDateTime.parse("2030-01-15T09:00:00");
        assertEquals(BookingWindowResult.END_NOT_AFTER_START, bookingWindowPolicy.evaluate(start, end));
    }
}


