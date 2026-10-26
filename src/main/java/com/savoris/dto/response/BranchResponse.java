package com.savoris.dto.response;

import com.savoris.constant.BranchStatus;
import com.savoris.entity.BranchEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {
    private Integer id;
    private String code;
    private String name;
    private String address;
    private String city;
    private String phone;
    private String email;
    private Double latitude;
    private Double longitude;
    private Integer managerId;
    private Integer kitchenCapacity;
    private Boolean takeawayPaused;
    private BranchStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BranchResponse from(BranchEntity entity) {
        BranchResponse response = new BranchResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setAddress(entity.getAddress());
        response.setCity(entity.getCity());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setLatitude(entity.getLatitude());
        response.setLongitude(entity.getLongitude());
        response.setManagerId(entity.getManagerId());
        response.setKitchenCapacity(entity.getKitchenCapacity());
        response.setTakeawayPaused(entity.getTakeawayPaused());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public static BranchResponse operationalContext(BranchResponse branch) {
        BranchResponse response = new BranchResponse();
        response.setId(branch.getId());
        response.setCode(branch.getCode());
        response.setName(branch.getName());
        response.setTakeawayPaused(branch.getTakeawayPaused());
        response.setStatus(branch.getStatus());
        return response;
    }
}
