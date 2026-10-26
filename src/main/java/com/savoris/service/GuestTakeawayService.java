package com.savoris.service;

import com.savoris.dto.request.WebTakeawayRequest;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicBranchResponse;
import com.savoris.dto.response.WebTakeawayResponse;
import com.savoris.dto.request.CreateOrderRequest;
import com.savoris.dto.response.TakeawayEstimateResponse;

import java.util.List;

public interface GuestTakeawayService {
    List<PublicBranchResponse> getBranches();
    // Branch menu for takeaway: same shape as a table's, without area and table
    GuestTableResponse getBranchMenu(Integer branchId);
    TakeawayEstimateResponse estimatePickup(Integer branchId, List<CreateOrderRequest.OrderItemRequest> items);
    WebTakeawayResponse placeOrder(WebTakeawayRequest request);
    WebTakeawayResponse getOrder(String token);
    // The guest says they have transferred: the order no longer expires, the cashier checks the bank
    WebTakeawayResponse claimTransfer(String token);
    void subscribe(String token, String endpoint, String p256dh, String auth);
    String pushPublicKey();
}
