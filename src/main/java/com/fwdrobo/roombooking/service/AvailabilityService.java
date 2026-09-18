package com.fwdrobo.roombooking.service;

import java.time.LocalDateTime;

import com.fwdrobo.roombooking.domain.Booking;
import com.fwdrobo.roombooking.repository.InMemoryBookingRepository;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {

    private final InMemoryBookingRepository bookingRepository;

    public AvailabilityService(InMemoryBookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public boolean isAvailable(String roomId, LocalDateTime start, LocalDateTime end) {
        boolean hasConflict = false;

        for (Booking existing : bookingRepository.findByRoomId(roomId)) {
            boolean candidateStartsBeforeExistingEnds = start.isBefore(existing.end());
            boolean existingStartsBeforeCandidateEnds = existing.start().isBefore(end);
            boolean overlaps = candidateStartsBeforeExistingEnds && existingStartsBeforeCandidateEnds;
            hasConflict = hasConflict || overlaps;
        }

        return !hasConflict;
    }
}
