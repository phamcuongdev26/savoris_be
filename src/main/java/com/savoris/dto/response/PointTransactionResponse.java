package com.savoris.dto.response;

import com.savoris.entity.PointTransactionEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionResponse {
    private Integer id;
    private Integer orderId;
    // Positive = earned / given back, negative = spent / taken back
    private Integer points;
    private String note;
    private LocalDateTime createdAt;

    public static PointTransactionResponse from(PointTransactionEntity entity) {
        return new PointTransactionResponse(entity.getId(), entity.getOrderId(), entity.getPoints(), entity.getNote(), entity.getCreatedAt());
    }
}
