package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateShiftRequest {
    @NotBlank(message = "Shift name is required")
    @Size(max = 50)
    private String name;

    // "07:00"
    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    // An end before the start means the shift runs past midnight, e.g. 18:00 - 02:00
    @NotNull(message = "End time is required")
    private LocalTime endTime;
}
