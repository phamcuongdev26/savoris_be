package com.savoris.dto.response;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.StaffEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffDetailResponse {
    private Integer id;
    private Integer branchId;
    private String code;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private StaffRole role;
    private StaffStatus status;
    private java.math.BigDecimal hourlyRate;
    private LocalDateTime hiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StaffDetailResponse from(StaffEntity entity) {
        StaffDetailResponse response = new StaffDetailResponse();
        response.setId(entity.getId());
        response.setBranchId(entity.getBranchId());
        response.setCode(entity.getCode());
        response.setFullName(entity.getFullName());
        response.setUsername(entity.getUsername());
        response.setEmail(entity.getEmail());
        response.setPhone(entity.getPhone());
        response.setRole(entity.getRole());
        response.setStatus(entity.getStatus());
        response.setHourlyRate(entity.getHourlyRate());
        response.setHiredAt(entity.getHiredAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
