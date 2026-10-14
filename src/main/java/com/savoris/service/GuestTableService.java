package com.savoris.service;

import com.savoris.dto.request.AddOrderItemsRequest;
import com.savoris.dto.response.GuestOrderResponse;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicMenuResponse;

import java.util.Optional;

public interface GuestTableService {
    PublicMenuResponse getChainMenu();
    GuestTableResponse getTable(String qrToken);
    Optional<GuestOrderResponse> getOpenOrder(String qrToken);
    GuestOrderResponse placeOrder(String qrToken, AddOrderItemsRequest request);
}
