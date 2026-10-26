package com.savoris.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.dto.request.AiChatRequest;
import com.savoris.dto.response.AiChatResponse;
import com.savoris.dto.response.PublicBranchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    @Test
    void answersCommonBranchQuestionsWithoutCallingAnUnconfiguredProvider() {
        AiModelClient ai = mock(AiModelClient.class);
        GuestTakeawayService guestService = mock(GuestTakeawayService.class);
        ReservationService reservationService = mock(ReservationService.class);
        when(ai.isConfigured()).thenReturn(false);
        when(guestService.getBranches()).thenReturn(List.of(new PublicBranchResponse(1, "CN01", "Hoàn Kiếm",
            "12 Lý Quốc Sư", true, 5, true, "Hà Nội", null, null, true,
            "024 7302 8181", "hoankiem@savoris.vn", List.of())));

        AiChatService service = new AiChatService(ai, new AiFaqService(guestService, reservationService), guestService, reservationService, new ObjectMapper());

        assertThat(service.chat(new AiChatRequest("Chi nhánh Hoàn Kiếm ở đâu?", List.of())).reply())
            .contains("12 Lý Quốc Sư");
        verify(ai, never()).complete(anyList(), anyList(), anyBoolean());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesProviderToolCallMetadataWhenSendingToolResultsBack() {
        AiModelClient ai = mock(AiModelClient.class);
        GuestTakeawayService guestService = mock(GuestTakeawayService.class);
        ReservationService reservationService = mock(ReservationService.class);
        Map<String, Object> extraContent = Map.of("google", Map.of("thought_signature", "sig-1"));
        when(ai.isConfigured()).thenReturn(true);
        when(guestService.getBranches()).thenReturn(List.of());
        when(ai.complete(anyList(), anyList(), anyBoolean()))
            .thenReturn(new AiModelClient.AiCompletion(null, List.of(
                new AiModelClient.AiToolCall("call-1", "get_branches", "{}", extraContent))))
            .thenReturn(new AiModelClient.AiCompletion("Có chi nhánh Hoàn Kiếm.", List.of()));

        AiChatService service = new AiChatService(ai, new AiFaqService(guestService, reservationService), guestService, reservationService, new ObjectMapper());
        service.chat(new AiChatRequest("Gợi ý một trải nghiệm bữa tối cho gia đình.", List.of()));

        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(ai, times(2)).complete(messages.capture(), anyList(), anyBoolean());
        assertThat(messages.getAllValues().get(0).get(0).get("content").toString())
            .contains("Kiến thức Savoris đã xác minh", "SePay", "không suy đoán", "AI Agent", "dữ liệu đơn hàng");
        Map<String, Object> assistant = messages.getAllValues().get(1).stream()
            .filter(message -> "assistant".equals(message.get("role"))).findFirst().orElseThrow();
        List<Map<String, Object>> calls = (List<Map<String, Object>>) assistant.get("tool_calls");
        assertThat(calls.get(0)).containsEntry("extra_content", extraContent);
    }

    @Test
    void agentModeRoutesEvenFaqLikeQuestionsThroughTheAiAgent() {
        AiModelClient ai = mock(AiModelClient.class);
        GuestTakeawayService guestService = mock(GuestTakeawayService.class);
        ReservationService reservationService = mock(ReservationService.class);
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), anyList(), anyBoolean()))
            .thenReturn(new AiModelClient.AiCompletion("Mình sẽ giúp bạn tìm thông tin phù hợp.", List.of()));

        AiChatService service = new AiChatService(ai, new AiFaqService(guestService, reservationService), guestService, reservationService, new ObjectMapper());
        AiChatResponse response = service.chat(new AiChatRequest("Thông tin chi nhánh và giờ mở cửa", List.of(), true));

        assertThat(response.reply()).contains("giúp bạn");
        verify(ai).complete(anyList(), anyList(), anyBoolean());
        verify(guestService, never()).getBranches();
    }

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

        AiChatService service = new AiChatService(ai, new AiFaqService(guestService, reservationService), guestService, reservationService, new ObjectMapper());
        service.chat(new AiChatRequest(message, List.of()));

        verify(reservationService, never()).createGuestReservation(org.mockito.ArgumentMatchers.any());
    }
}
