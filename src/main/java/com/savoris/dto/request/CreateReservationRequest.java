package com.savoris.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
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
    @NotNull(message = "Branch ID là bắt buộc")
    private Integer branchId;

    @NotBlank(message = "Tên khách là bắt buộc")
    private String customerName;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    private String customerPhone;

    @NotNull(message = "Ngày đặt bàn là bắt buộc")
    @Future(message = "Ngày đặt bàn phải ở tương lai")
    private LocalDateTime reservationDate;

    @NotNull(message = "Số khách là bắt buộc")
    @Positive(message = "Số khách phải lớn hơn 0")
    private Integer numberOfGuests;

    private String notes;

    private Boolean overrideCapacity;
}
