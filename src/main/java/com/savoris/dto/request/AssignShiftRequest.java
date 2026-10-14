package com.savoris.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignShiftRequest {
    @NotNull(message = "Staff ID is required")
    private Integer staffId;

    @NotNull(message = "Work date is required")
    private LocalDate workDate;

    private String note;
}
