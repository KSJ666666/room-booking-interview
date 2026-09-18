package com.fwdrobo.roombooking.service;

import java.time.LocalDateTime;

import com.fwdrobo.roombooking.domain.Booking;
import com.fwdrobo.roombooking.domain.BookingWindowPolicy;
import com.fwdrobo.roombooking.domain.BookingWindowResult;
import com.fwdrobo.roombooking.repository.InMemoryBookingRepository;
import com.fwdrobo.roombooking.repository.InMemoryRoomRepository;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private final InMemoryRoomRepository roomRepository;
    private final InMemoryBookingRepository bookingRepository;
    private final BookingWindowPolicy bookingWindowPolicy;
    private final AvailabilityService availabilityService;

    public BookingService(
            InMemoryRoomRepository roomRepository,
            InMemoryBookingRepository bookingRepository,
            BookingWindowPolicy bookingWindowPolicy,
            AvailabilityService availabilityService
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.bookingWindowPolicy = bookingWindowPolicy;
        this.availabilityService = availabilityService;
    }

    public Booking get(String roomId, String bookingId) {
        requireRoom(roomId);
        return bookingRepository.findByIdAndRoomId(bookingId, roomId)
                .orElseThrow(() -> new BookingNotFoundException(roomId, bookingId));
    }

    public Booking create(String roomId, LocalDateTime start, LocalDateTime end) {

        if (isAvailable(roomId, start, end)) {
            return bookingRepository.create(roomId, start, end);
        }
        throw new BookingConflictException(roomId, start, end);
    }

    public boolean isAvailable(String roomId, LocalDateTime start, LocalDateTime end) {
        requireRoom(roomId);
        BookingWindowResult result = bookingWindowPolicy.evaluate(start, end);
        if (result != BookingWindowResult.VALID) {
            throw new InvalidBookingWindowException(result);
        }
        return availabilityService.isAvailable(roomId, start, end);
    }

    private void requireRoom(String roomId) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
    }
}
