package com.savoris.service.impl;

import com.savoris.constant.StaffStatus;
import com.savoris.dto.request.AssignShiftRequest;
import com.savoris.dto.request.CreateShiftRequest;
import com.savoris.dto.response.ShiftResponse;
import com.savoris.dto.response.StaffShiftResponse;
import com.savoris.dto.response.TimesheetResponse;
import com.savoris.entity.ShiftEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.entity.StaffShiftEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.ShiftRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.repository.StaffShiftRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.ShiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    // Staff may check in a little before their shift starts
    private static final long EARLY_CHECK_IN_MINUTES = 30;
    private static final long MAX_RANGE_DAYS = 62;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final ShiftRepository shiftRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final StaffRepository staffRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Override
    @Transactional
    public ShiftResponse createShift(Integer actorId, Integer branchId, CreateShiftRequest request) {
        branchAccessGuard.check(actorId, branchId);
        if (request.getStartTime().equals(request.getEndTime())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "A shift cannot start and end at the same time");
        }
        ShiftEntity shift = new ShiftEntity();
        shift.setBranchId(branchId);
        shift.setName(request.getName());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        return ShiftResponse.from(shiftRepository.save(shift));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftResponse> getShifts(Integer actorId, Integer branchId) {
        branchAccessGuard.check(actorId, branchId);
        return shiftRepository.findByBranchIdOrderByStartTime(branchId).stream().map(ShiftResponse::from).toList();
    }

    @Override
    @Transactional
    public StaffShiftResponse assign(Integer actorId, Integer shiftId, AssignShiftRequest request) {
        ShiftEntity shift = findShift(shiftId);
        branchAccessGuard.check(actorId, shift.getBranchId());

        StaffEntity staff = staffRepository.findById(request.getStaffId())
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND, "Staff not found: " + request.getStaffId()));
        if (!Objects.equals(staff.getBranchId(), shift.getBranchId()) || staff.getStatus() == StaffStatus.RESIGNED) {
            throw new AppException(ErrorCode.INVALID_REQUEST, staff.getFullName() + " is not active staff of this branch");
        }
        if (request.getWorkDate().isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Cannot schedule a shift in the past");
        }
        if (staffShiftRepository.existsByStaffIdAndShiftIdAndWorkDate(staff.getId(), shiftId, request.getWorkDate())) {
            throw new AppException(ErrorCode.CONFLICT, staff.getFullName() + " is already on this shift that day");
        }

        StaffShiftEntity assignment = new StaffShiftEntity();
        assignment.setStaffId(staff.getId());
        assignment.setShiftId(shiftId);
        assignment.setWorkDate(request.getWorkDate());
        assignment.setNote(request.getNote());
        StaffShiftEntity saved = staffShiftRepository.save(assignment);
        log.info("Scheduled {} on {} ({})", staff.getFullName(), request.getWorkDate(), shift.getName());
        return StaffShiftResponse.from(saved, shift, staff);
    }

    @Override
    @Transactional
    public void unassign(Integer actorId, Integer assignmentId) {
        StaffShiftEntity assignment = findAssignment(assignmentId);
        branchAccessGuard.check(actorId, findShift(assignment.getShiftId()).getBranchId());
        if (assignment.getCheckInAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATE, "The staff member already checked in for this shift");
        }
        staffShiftRepository.delete(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffShiftResponse> getSchedule(Integer actorId, Integer branchId, LocalDate from, LocalDate to) {
        branchAccessGuard.check(actorId, branchId);
        checkRange(from, to);
        return toResponses(staffShiftRepository.findByBranchAndDates(branchId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffShiftResponse> getMySchedule(Integer actorId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return toResponses(staffShiftRepository.findByStaffIdAndWorkDateBetweenOrderByWorkDate(actorId, from, to));
    }

    @Override
    @Transactional
    public StaffShiftResponse checkIn(Integer actorId, Integer assignmentId) {
        StaffShiftEntity assignment = findOwnAssignment(actorId, assignmentId);
        ShiftEntity shift = findShift(assignment.getShiftId());
        if (assignment.getCheckInAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATE, "Already checked in at " + assignment.getCheckInAt());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = assignment.getWorkDate().atTime(shift.getStartTime());
        if (now.isBefore(start.minusMinutes(EARLY_CHECK_IN_MINUTES)) || now.isAfter(shiftEnd(assignment, shift))) {
            throw new AppException(ErrorCode.INVALID_STATE,
                "Check-in is open from " + EARLY_CHECK_IN_MINUTES + " minutes before the shift until it ends");
        }
        assignment.setCheckInAt(now);
        staffShiftRepository.save(assignment);
        return StaffShiftResponse.from(assignment, shift, staffRepository.findById(actorId).orElse(null));
    }

    @Override
    @Transactional
    public StaffShiftResponse checkOut(Integer actorId, Integer assignmentId) {
        StaffShiftEntity assignment = findOwnAssignment(actorId, assignmentId);
        if (assignment.getCheckInAt() == null) {
            throw new AppException(ErrorCode.INVALID_STATE, "Check in first");
        }
        if (assignment.getCheckOutAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATE, "Already checked out at " + assignment.getCheckOutAt());
        }
        assignment.setCheckOutAt(LocalDateTime.now());
        staffShiftRepository.save(assignment);
        return StaffShiftResponse.from(assignment, findShift(assignment.getShiftId()), staffRepository.findById(actorId).orElse(null));
    }

    // Hours actually worked (check-in to check-out) x hourly rate, per staff member
    @Override
    @Transactional(readOnly = true)
    public List<TimesheetResponse> getTimesheet(Integer actorId, Integer branchId, LocalDate from, LocalDate to) {
        branchAccessGuard.check(actorId, branchId);
        checkRange(from, to);
        List<StaffShiftEntity> assignments = staffShiftRepository.findByBranchAndDates(branchId, from, to);
        Map<Integer, StaffEntity> staff = staffRepository
            .findAllById(assignments.stream().map(StaffShiftEntity::getStaffId).distinct().toList()).stream()
            .collect(Collectors.toMap(StaffEntity::getId, Function.identity()));

        Map<Integer, List<StaffShiftEntity>> byStaff = assignments.stream()
            .collect(Collectors.groupingBy(StaffShiftEntity::getStaffId, LinkedHashMap::new, Collectors.toList()));
        List<TimesheetResponse> rows = new ArrayList<>();
        for (Map.Entry<Integer, List<StaffShiftEntity>> entry : byStaff.entrySet()) {
            StaffEntity person = staff.get(entry.getKey());
            int worked = 0;
            int missed = 0;
            long minutes = 0;
            for (StaffShiftEntity assignment : entry.getValue()) {
                if (assignment.getCheckInAt() != null && assignment.getCheckOutAt() != null) {
                    worked++;
                    minutes += Duration.between(assignment.getCheckInAt(), assignment.getCheckOutAt()).toMinutes();
                } else if (assignment.getCheckInAt() == null && assignment.getWorkDate().isBefore(LocalDate.now())) {
                    missed++;
                }
            }
            BigDecimal hours = BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
            BigDecimal rate = person.getHourlyRate() != null ? person.getHourlyRate() : BigDecimal.ZERO;
            BigDecimal wage = BigDecimal.valueOf(minutes).multiply(rate).divide(SIXTY, 0, RoundingMode.HALF_UP);
            rows.add(new TimesheetResponse(person.getId(), person.getFullName(), person.getRole(), worked, missed,
                minutes, hours, rate, wage));
        }
        return rows;
    }

    private LocalDateTime shiftEnd(StaffShiftEntity assignment, ShiftEntity shift) {
        LocalDate endDate = shift.getEndTime().isBefore(shift.getStartTime())
            ? assignment.getWorkDate().plusDays(1)
            : assignment.getWorkDate();
        return endDate.atTime(shift.getEndTime());
    }

    private void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Date range must be at most " + MAX_RANGE_DAYS + " days, from before to");
        }
    }

    private List<StaffShiftResponse> toResponses(List<StaffShiftEntity> assignments) {
        Map<Integer, ShiftEntity> shifts = shiftRepository
            .findAllById(assignments.stream().map(StaffShiftEntity::getShiftId).distinct().toList()).stream()
            .collect(Collectors.toMap(ShiftEntity::getId, Function.identity()));
        Map<Integer, StaffEntity> staff = staffRepository
            .findAllById(assignments.stream().map(StaffShiftEntity::getStaffId).distinct().toList()).stream()
            .collect(Collectors.toMap(StaffEntity::getId, Function.identity()));
        return assignments.stream()
            .map(a -> StaffShiftResponse.from(a, shifts.get(a.getShiftId()), staff.get(a.getStaffId())))
            .toList();
    }

    private StaffShiftEntity findOwnAssignment(Integer actorId, Integer assignmentId) {
        StaffShiftEntity assignment = findAssignment(assignmentId);
        if (!assignment.getStaffId().equals(actorId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "You can only check in and out of your own shifts");
        }
        return assignment;
    }

    private ShiftEntity findShift(Integer shiftId) {
        return shiftRepository.findById(shiftId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Shift not found: " + shiftId));
    }

    private StaffShiftEntity findAssignment(Integer assignmentId) {
        return staffShiftRepository.findById(assignmentId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Shift assignment not found: " + assignmentId));
    }
}
