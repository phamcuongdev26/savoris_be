package com.savoris.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateReservationRequest {
    @NotNull(message = "Branch ID is required")
    private Integer branchId;

    @NotNull(message = "Customer ID is required")
    private Integer customerId;

    @NotNull(message = "Reservation date is required")
    @Future(message = "Reservation date must be in the future")
    private LocalDateTime reservationDate;

    @NotNull(message = "Number of guests is required")
    @Positive(message = "Number of guests must be positive")
    private Integer numberOfGuests;

    private String notes;
}
