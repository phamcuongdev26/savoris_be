package com.savoris.service;

import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.response.ReservationResponse;

import java.util.List;

public interface ReservationService {
    ReservationResponse createReservation(Integer staffId, CreateReservationRequest request);
    ReservationResponse getReservation(Integer staffId, Integer reservationId);
    ReservationResponse confirmReservation(Integer staffId, Integer reservationId);
    ReservationResponse cancelReservation(Integer staffId, Integer reservationId);
    List<ReservationResponse> getReservationsByBranch(Integer staffId, Integer branchId);
    List<ReservationResponse> getReservationsByCustomer(Integer customerId);
}
