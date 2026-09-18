package com.fwdrobo.roombooking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fwdrobo.roombooking.repository.InMemoryBookingRepository;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;



@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryBookingRepository bookingRepository;


    @Test
    void returnsExistingBooking() throws Exception {
        mockMvc.perform(get("/rooms/room-101/bookings/booking-1011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("booking-1011"))
                .andExpect(jsonPath("$.roomId").value("room-101"))
                .andExpect(jsonPath("$.start").value("2030-01-15T09:00:00"))
                .andExpect(jsonPath("$.end").value("2030-01-15T09:30:00"));
    }

    @Test
    void returnsNotFoundForMissingRoom() throws Exception {
        mockMvc.perform(get("/rooms/room-missing/bookings/booking-1011"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.path")
                        .value("/rooms/room-missing/bookings/booking-1011"));
    }
    @Test
    void returnsNotFoundForMissingBooking() throws Exception {
        mockMvc.perform(get("/rooms/room-101/bookings/booking-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"))
                .andExpect(jsonPath("$.path")
                        .value("/rooms/room-101/bookings/booking-missing"));
    }
    @Test
    void returnsUnavailableForOverlappingWindow() throws Exception {
        mockMvc.perform(get("/rooms/room-202/availability")
                        .param("start", "2030-01-15T10:15:00")
                        .param("end", "2030-01-15T10:45:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void returnsAvailableForNonOverlappingWindow() throws Exception {
        mockMvc.perform(get("/rooms/room-202/availability")
                        .param("start", "2030-01-15T11:00:00")
                        .param("end", "2030-01-15T11:30:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void returnsAvailableForAdjacentWindow() throws Exception {
        mockMvc.perform(get("/rooms/room-202/availability")
                        .param("start", "2030-01-15T10:30:00")
                        .param("end", "2030-01-15T11:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
    @Test
    void createsBookingSuccessfully() throws Exception {
        mockMvc.perform(post("/rooms/room-202/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T14:00:00\",\"end\":\"2030-01-15T14:30:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(startsWith("booking-")))
                .andExpect(jsonPath("$.roomId").value("room-202"))
                .andExpect(jsonPath("$.start").value("2030-01-15T14:00:00"))
                .andExpect(jsonPath("$.end").value("2030-01-15T14:30:00"));
    }

    @Test
    void returnsCreatedBookingAtLocation() throws Exception {
        String location = mockMvc.perform(post("/rooms/room-202/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T15:00:00\",\"end\":\"2030-01-15T15:30:00\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertNotNull(location);
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-202"))
                .andExpect(jsonPath("$.end").value("2030-01-15T15:30:00"));
    }

    @Test
    void createsBookingAdjacentToExistingBooking() throws Exception {
        mockMvc.perform(post("/rooms/room-101/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T09:30:00\",\"end\":\"2030-01-15T10:30:00\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidBookingWindow() throws Exception {
        mockMvc.perform(post("/rooms/room-202/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T14:00:00\",\"end\":\"2030-01-15T14:10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOKING_WINDOW"));
    }

    @Test
    void returnsNotFoundWhenCreatingForMissingRoom() throws Exception {
        mockMvc.perform(post("/rooms/room-missing/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T14:00:00\",\"end\":\"2030-01-15T14:30:00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void returnsConflictForOverlappingBooking() throws Exception {
        mockMvc.perform(post("/rooms/room-202/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T10:15:00\",\"end\":\"2030-01-15T10:45:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"));
    }

    @Test
    void doesNotStoreBookingWhenRequestFails() throws Exception {
        long countBefore = bookingRepository.countByRoomId("room-202");

        mockMvc.perform(post("/rooms/room-202/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2030-01-15T10:15:00\",\"end\":\"2030-01-15T10:45:00\"}"))
                .andExpect(status().isConflict());

        assertEquals(countBefore, bookingRepository.countByRoomId("room-202"));
    }

}
