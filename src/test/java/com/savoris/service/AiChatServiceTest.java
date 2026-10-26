package com.savoris.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.dto.request.AiChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"Đừng đặt bàn giúp tôi", "I won't book a table", "I will not book a table", "I do not want to book a table"})
    void doesNotCreateReservationWhenLatestMessageNegatesBooking(String message) {
        AiModelClient ai = mock(AiModelClient.class);
        GuestTakeawayService guestService = mock(GuestTakeawayService.class);
        ReservationService reservationService = mock(ReservationService.class);
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), anyList(), anyBoolean()))
            .thenReturn(new AiModelClient.AiCompletion(null, List.of(new AiModelClient.AiToolCall(
                "call-1", "create_reservation",
                "{\"branchId\":1,\"name\":\"An\",\"phone\":\"0912345678\",\"date\":\"2099-01-01T19:00:00\",\"guests\":2}"))))
            .thenReturn(new AiModelClient.AiCompletion("Mình sẽ không đặt bàn.", List.of()));

        AiChatService service = new AiChatService(ai, guestService, reservationService, new ObjectMapper());
        service.chat(new AiChatRequest(message, List.of()));

        verify(reservationService, never()).createGuestReservation(org.mockito.ArgumentMatchers.any());
    }
}
