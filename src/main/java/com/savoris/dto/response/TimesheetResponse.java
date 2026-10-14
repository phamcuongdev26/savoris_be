package com.savoris.dto.response;

import com.savoris.constant.StaffRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetResponse {
    private Integer staffId;
    private String staffName;
    private StaffRole role;
    private int shiftsWorked;
    // Shifts that were scheduled but never checked in
    private int shiftsMissed;
    private long minutesWorked;
    private BigDecimal hoursWorked;
    private BigDecimal hourlyRate;
    private BigDecimal wage;
}
