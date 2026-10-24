package com.savoris.service;

import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.request.GuestReservationRequest;
import com.savoris.dto.response.ReservationResponse;
import com.savoris.dto.response.SlotAvailabilityResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReservationService {
    ReservationResponse createReservation(Integer staffId, CreateReservationRequest request);
    ReservationResponse getReservation(Integer staffId, Integer reservationId);
    ReservationResponse confirmReservation(Integer staffId, Integer reservationId);
    ReservationResponse cancelReservation(Integer staffId, Integer reservationId);
    ReservationResponse markNoShow(Integer staffId, Integer reservationId);
    List<ReservationResponse> getReservationsByBranch(Integer staffId, Integer branchId);
    List<ReservationResponse> getReservationsByCustomer(Integer customerId);

    /** Public: available time slots for a given branch + date. */
    List<SlotAvailabilityResponse> getAvailableSlots(Integer branchId, LocalDate date);

    /** Public: create a reservation from the guest-facing booking form (no customerId needed). */
    ReservationResponse createGuestReservation(GuestReservationRequest request);
}
