package com.savoris.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.savoris.entity.OrderItemEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemResponseTest {
    @Test
    void serializesReadyAtAsAnUnambiguousInstant() throws Exception {
        LocalDateTime readyAt = LocalDateTime.of(2026, 10, 28, 12, 0);
        OrderItemEntity entity = new OrderItemEntity();
        entity.setReadyAt(readyAt);

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String serialized = mapper.writeValueAsString(OrderItemResponse.from(entity));

        assertThat(mapper.readTree(serialized).path("readyAt").asText())
            .isEqualTo(readyAt.atZone(ZoneId.systemDefault()).toInstant().toString());
    }
}
