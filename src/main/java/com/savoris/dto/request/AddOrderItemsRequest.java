package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddOrderItemsRequest {
    @NotEmpty(message = "Add at least one dish")
    @Valid
    private List<CreateOrderRequest.OrderItemRequest> items;
}
