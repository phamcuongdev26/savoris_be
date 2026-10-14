package com.savoris.dto.response;

import com.savoris.constant.TableStatus;
import com.savoris.entity.DiningTableEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {
    private Integer id;
    private Integer areaId;
    private String name;
    private Integer capacity;
    private TableStatus status;
    private String qrToken;

    public static TableResponse from(DiningTableEntity entity) {
        return new TableResponse(entity.getId(), entity.getAreaId(), entity.getName(), entity.getCapacity(), entity.getStatus(),
            entity.getQrToken());
    }
}
