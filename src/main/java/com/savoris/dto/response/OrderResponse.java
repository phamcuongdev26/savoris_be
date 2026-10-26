package com.savoris.dto.response;

import com.savoris.constant.OrderStatus;
import com.savoris.constant.OrderType;
import com.savoris.constant.PaymentMethod;
import com.savoris.entity.OrderEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Integer id;
    private String code;
    private Integer branchId;
    private Integer tableId;
    private Integer staffId;
    private OrderType orderType;
    private OrderStatus status;
    private String notes;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
    // Web takeaway only
    private String guestName;
    private String guestPhone;
    private PaymentMethod payMethod;
    private LocalDateTime transferClaimedAt;
    private LocalDateTime refundDueAt;

    public static OrderResponse from(OrderEntity entity, List<OrderItemResponse> items) {
        OrderResponse response = new OrderResponse();
        response.setId(entity.getId());
        response.setCode(entity.getCode());
        response.setBranchId(entity.getBranchId());
        response.setTableId(entity.getTableId());
        response.setStaffId(entity.getOpenedBy());
        response.setOrderType(entity.getType());
        response.setStatus(entity.getStatus());
        response.setNotes(entity.getNote());
        response.setSubtotal(entity.getSubtotal());
        response.setVatAmount(entity.getVatAmount());
        response.setTotalAmount(entity.getTotalAmount());
        response.setCreatedAt(entity.getOpenedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setItems(items);
        response.setGuestName(entity.getGuestName());
        response.setGuestPhone(entity.getGuestPhone());
        response.setPayMethod(entity.getPayMethod());
        response.setTransferClaimedAt(entity.getTransferClaimedAt());
        response.setRefundDueAt(entity.getRefundSettledAt() == null ? entity.getRefundDueAt() : null);
        return response;
    }
}
