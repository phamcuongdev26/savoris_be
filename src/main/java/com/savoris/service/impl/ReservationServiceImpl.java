package com.savoris.service.impl;

import com.savoris.constant.ReservationStatus;
import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.response.ReservationResponse;
import com.savoris.entity.ReservationEntity;
import com.savoris.repository.ReservationRepository;
import com.savoris.service.ReservationService;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.entity.CustomerEntity;
import com.savoris.repository.CustomerRepository;
import com.savoris.security.BranchAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Override
    @Transactional
    public ReservationResponse createReservation(Integer staffId, CreateReservationRequest request) {
        branchAccessGuard.check(staffId, request.getBranchId());
        CustomerEntity customer = customerRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Customer not found: " + request.getCustomerId()));

        ReservationEntity reservation = new ReservationEntity();
        reservation.setBranchId(request.getBranchId());
        reservation.setCustomerId(customer.getId());
        // Walk-in customers may be registered with a phone number only
        reservation.setCustomerName(customer.getFullName() != null ? customer.getFullName() : customer.getPhone());
        reservation.setCustomerPhone(customer.getPhone());
        reservation.setGuestCount(request.getNumberOfGuests());
        reservation.setReservedAt(request.getReservationDate());
        reservation.setNote(request.getNotes());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedBy(staffId);

        ReservationEntity saved = reservationRepository.save(reservation);
        log.info("Created reservation: {} for date: {}", saved.getId(), request.getReservationDate());
        return ReservationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Integer staffId, Integer reservationId) {
        return ReservationResponse.from(findAccessible(staffId, reservationId));
    }

    @Override
    @Transactional
    public ReservationResponse confirmReservation(Integer staffId, Integer reservationId) {
        ReservationEntity reservation = findAccessible(staffId, reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE, "Reservation is already " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("Confirmed reservation: {}", reservationId);
        return ReservationResponse.from(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse cancelReservation(Integer staffId, Integer reservationId) {
        ReservationEntity reservation = findAccessible(staffId, reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new AppException(ErrorCode.INVALID_STATE, "Reservation is already " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("Cancelled reservation: {}", reservationId);
        return ReservationResponse.from(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByBranch(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return reservationRepository.findByBranchIdAndStatus(branchId, ReservationStatus.PENDING)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    private ReservationEntity findAccessible(Integer staffId, Integer reservationId) {
        ReservationEntity reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Reservation not found: " + reservationId));
        branchAccessGuard.check(staffId, reservation.getBranchId());
        return reservation;
    }

    @Override
    public List<ReservationResponse> getReservationsByCustomer(Integer customerId) {
        return reservationRepository.findByCustomerId(customerId)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }
}
