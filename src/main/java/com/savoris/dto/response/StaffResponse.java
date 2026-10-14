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
public class StaffResponse {
    private Integer id;
    private Integer branchId;
    private String branchName;
    private String code;
    private String fullName;
    private String phone;
    private String email;
    private String username;
    private StaffRole role;
    private StaffStatus status;
    private Integer hourlyRate;
    private LocalDateTime hiredAt;

    public static StaffResponse from(StaffEntity entity, String branchName) {
        StaffResponse response = new StaffResponse();
        response.setId(entity.getId());
        response.setBranchId(entity.getBranchId());
        response.setBranchName(branchName);
        response.setCode(entity.getCode());
        response.setFullName(entity.getFullName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setUsername(entity.getUsername());
        response.setRole(entity.getRole());
        response.setStatus(entity.getStatus());
        response.setHourlyRate(entity.getHourlyRate() != null ? entity.getHourlyRate().intValue() : null);
        response.setHiredAt(entity.getHiredAt());
        return response;
    }

    public static StaffResponse from(StaffEntity entity) {
        return from(entity, null);
    }
}
