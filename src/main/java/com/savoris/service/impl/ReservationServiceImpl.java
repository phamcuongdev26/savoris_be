package com.savoris.service.impl;

import com.savoris.constant.ReservationStatus;
import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.request.GuestReservationRequest;
import com.savoris.dto.response.ReservationResponse;
import com.savoris.dto.response.SlotAvailabilityResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final JdbcTemplate jdbc;

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
    @Transactional
    public ReservationResponse markNoShow(Integer staffId, Integer reservationId) {
        ReservationEntity reservation = findAccessible(staffId, reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new AppException(ErrorCode.INVALID_STATE, "Only pending or confirmed reservations can be marked no-show");
        }
        reservation.setStatus(ReservationStatus.NO_SHOW);
        reservation.setUpdatedAt(LocalDateTime.now());
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByBranch(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return reservationRepository.findByBranchId(branchId)
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

    // ── Public slot availability ─────────────────────────────────────────────

    private static final int SLOT_MINUTES     = 30;
    private static final int MAX_GUESTS_SLOT  = 40;

    @Override
    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getAvailableSlots(Integer branchId, LocalDate date) {
        List<LocalDateTime> slots = generateSlots(branchId, date);

        List<SlotAvailabilityResponse> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

        for (LocalDateTime slot : slots) {
            LocalDateTime slotEnd = slot.plusMinutes(SLOT_MINUTES);
            int booked = reservationRepository
                .findByBranchIdAndReservedAtBetweenAndStatusNotIn(branchId, slot, slotEnd,
                    List.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW))
                .stream().mapToInt(ReservationEntity::getGuestCount).sum();

            boolean available = booked < MAX_GUESTS_SLOT && slot.isAfter(now);

            result.add(SlotAvailabilityResponse.builder()
                .slotTime(slot)
                .bookedGuests(booked)
                .maxCapacity(MAX_GUESTS_SLOT)
                .available(available)
                .build());
        }

        // Fill suggestedAlternative: for each full slot, point to the next available one
        for (int i = 0; i < result.size(); i++) {
            if (!result.get(i).isAvailable()) {
                for (int j = i + 1; j < result.size(); j++) {
                    if (result.get(j).isAvailable()) {
                        result.get(i).setSuggestedAlternative(result.get(j).getSlotTime());
                        break;
                    }
                }
            }
        }
        return result;
    }

    private List<LocalDateTime> generateSlots(Integer branchId, LocalDate date) {
        List<LocalDateTime> slots = new ArrayList<>();
        int weekday = date.getDayOfWeek().getValue();
        List<LocalTime[]> windows = jdbc.query(
            "select opens_at, closes_at from opening_hour where branch_id=? and weekday=? and closed=false and opens_at is not null and closes_at is not null",
            (rs, row) -> new LocalTime[]{rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class)},
            branchId, weekday);
        for (LocalTime[] window : windows) {
            LocalTime cursor = window[0].withSecond(0).withNano(0);
            while (cursor.isBefore(window[1])) {
                LocalDateTime slot = date.atTime(cursor);
                if (!slot.plusMinutes(SLOT_MINUTES).toLocalTime().isAfter(window[1])) slots.add(slot);
                cursor = cursor.plusMinutes(SLOT_MINUTES);
            }
        }
        return slots;
    }

    @Override
    @Transactional
    public ReservationResponse createGuestReservation(GuestReservationRequest request) {
        // Validate slot availability
        LocalDateTime slot = request.getReservationDate().withSecond(0).withNano(0);
        validateOpeningHours(request.getBranchId(), slot);
        LocalDateTime slotEnd = slot.plusMinutes(SLOT_MINUTES);
        int booked = reservationRepository
            .findByBranchIdAndReservedAtBetweenAndStatusNotIn(request.getBranchId(), slot, slotEnd,
                List.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW))
            .stream().mapToInt(ReservationEntity::getGuestCount).sum();
        if (booked + request.getNumberOfGuests() > MAX_GUESTS_SLOT) {
            throw new AppException(ErrorCode.INVALID_STATE,
                "Slot " + slot + " is full. Please choose another time.");
        }

        ReservationEntity entity = new ReservationEntity();
        entity.setBranchId(request.getBranchId());
        entity.setCustomerName(request.getGuestName());
        entity.setCustomerPhone(request.getGuestPhone());
        entity.setGuestCount(request.getNumberOfGuests());
        entity.setReservedAt(request.getReservationDate());
        entity.setNote(request.getNotes());
        entity.setStatus(ReservationStatus.PENDING);

        ReservationEntity saved = reservationRepository.save(entity);
        log.info("Guest reservation {} for branch {} at {}", saved.getId(), request.getBranchId(), slot);
        return ReservationResponse.from(saved);
    }

    private void validateOpeningHours(Integer branchId, LocalDateTime slot) {
        if (slot.getSecond() != 0 || slot.getNano() != 0 || slot.getMinute() % SLOT_MINUTES != 0) {
            throw new AppException(ErrorCode.INVALID_STATE, "Vui lòng chọn giờ theo khung 30 phút");
        }
        int weekday = slot.getDayOfWeek().getValue();
        List<LocalTime[]> windows = jdbc.query(
            "select opens_at, closes_at from opening_hour where branch_id=? and weekday=? and closed=false and opens_at is not null and closes_at is not null",
            (rs, row) -> new LocalTime[]{rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class)},
            branchId, weekday);
        LocalTime time = slot.toLocalTime();
        boolean inHours = windows.stream().anyMatch(window -> !time.isBefore(window[0])
            && !time.plusMinutes(SLOT_MINUTES).isAfter(window[1]));
        if (!slot.isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))) || !inHours) {
            throw new AppException(ErrorCode.INVALID_STATE, "Giờ đặt bàn nằm ngoài giờ hoạt động của chi nhánh");
        }
    }
}
