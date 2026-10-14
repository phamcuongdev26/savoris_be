package com.savoris.dto.response;

import com.savoris.entity.ShiftEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.entity.StaffShiftEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffShiftResponse {
    private Integer id;
    private Integer staffId;
    private String staffName;
    private Integer shiftId;
    private String shiftName;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    // Filled once the staff member has checked out
    private Long workedMinutes;
    private String note;

    public static StaffShiftResponse from(StaffShiftEntity assignment, ShiftEntity shift, StaffEntity staff) {
        Long worked = assignment.getCheckInAt() != null && assignment.getCheckOutAt() != null
            ? Duration.between(assignment.getCheckInAt(), assignment.getCheckOutAt()).toMinutes()
            : null;
        return new StaffShiftResponse(assignment.getId(), assignment.getStaffId(), staff != null ? staff.getFullName() : null,
            shift.getId(), shift.getName(), assignment.getWorkDate(), shift.getStartTime(), shift.getEndTime(),
            assignment.getCheckInAt(), assignment.getCheckOutAt(), worked, assignment.getNote());
    }
}
