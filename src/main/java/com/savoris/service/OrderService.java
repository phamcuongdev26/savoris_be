package com.savoris.service;

import com.savoris.constant.VoidResolution;
import com.savoris.dto.request.AddOrderItemsRequest;
import com.savoris.dto.request.CreateOrderRequest;
import com.savoris.dto.request.WebTakeawayRequest;
import com.savoris.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(Integer staffId, CreateOrderRequest request);
    OrderResponse getOrder(Integer staffId, Integer orderId);
    OrderResponse completeOrder(Integer staffId, Integer orderId);
    OrderResponse cancelOrder(Integer staffId, Integer orderId);
    OrderResponse addItems(Integer staffId, Integer orderId, AddOrderItemsRequest request);
    OrderResponse placeGuestOrder(String qrToken, AddOrderItemsRequest request);
    // Returns the public tracking token of the new order
    String placeWebTakeaway(WebTakeawayRequest request);
    // Web takeaway never paid within the time limit (and the guest did not say they transferred) is cancelled
    int cancelExpiredWebTakeaways(int minutes);
    OrderResponse cancelItem(Integer staffId, Integer orderId, Integer itemId, String reason);
    /** Waiter flags a cooking/ready item for chef review instead of direct-cancelling it */
    OrderResponse requestVoid(Integer staffId, Integer orderId, Integer itemId, String reason);
    /** Chef resolves a void request: accept (cancel + restock), remake (redo free), or charge (keep on bill) */
    OrderResponse resolveVoid(Integer staffId, Integer orderId, Integer itemId, VoidResolution resolution);
    List<OrderResponse> getOrdersByBranch(Integer staffId, Integer branchId);
    // Cancelled transfer orders the counter still has to pay back
    List<OrderResponse> getRefundsDue(Integer staffId, Integer branchId);
    OrderResponse settleRefund(Integer staffId, Integer orderId);
}
