package com.savoris.dto.response;

import com.savoris.entity.ShiftEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
    private Integer id;
    private Integer branchId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;

    public static ShiftResponse from(ShiftEntity entity) {
        return new ShiftResponse(entity.getId(), entity.getBranchId(), entity.getName(), entity.getStartTime(), entity.getEndTime());
    }
}
