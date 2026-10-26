package com.savoris.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.dto.request.AiChatRequest;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.service.AiChatService;
import com.savoris.service.KitchenForecastService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFeatureAvailabilityTest {
    private final AiModelClient unconfigured = new AiModelClient() {
        @Override public boolean isConfigured() { return false; }
        @Override public AiCompletion complete(java.util.List<java.util.Map<String, Object>> messages,
                java.util.List<java.util.Map<String, Object>> tools, boolean jsonMode) {
            throw new AssertionError("Provider must not be called when unconfigured");
        }
    };

    @Test
    void chatExplainsProviderMustBeConfigured() {
        AiChatService service = new AiChatService(unconfigured, new com.savoris.service.AiFaqService(null, null), null, null, new ObjectMapper());
        assertThatThrownBy(() -> service.chat(new AiChatRequest("Xin chào", null)))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
    }

    @Test
    void forecastExplainsProviderMustBeConfigured() {
        KitchenForecastService service = new KitchenForecastService(unconfigured, null, null, null, null, null, new ObjectMapper());
        assertThatThrownBy(() -> service.forecast(1, 1))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
    }
}
