package com.savoris.dto.response;

import com.savoris.constant.ReservationStatus;
import com.savoris.entity.ReservationEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private Integer id;
    private Integer branchId;
    private Integer customerId;
    private String customerName;
    private String customerPhone;
    private Integer tableId;
    private LocalDateTime reservationDate;
    private Integer numberOfGuests;
    private ReservationStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReservationResponse from(ReservationEntity entity) {
        ReservationResponse response = new ReservationResponse();
        response.setId(entity.getId());
        response.setBranchId(entity.getBranchId());
        response.setCustomerId(entity.getCustomerId());
        response.setCustomerName(entity.getCustomerName());
        response.setCustomerPhone(entity.getCustomerPhone());
        response.setTableId(entity.getTableId());
        response.setReservationDate(entity.getReservedAt());
        response.setNumberOfGuests(entity.getGuestCount());
        response.setStatus(entity.getStatus());
        response.setNotes(entity.getNote());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
