package com.savoris;

import com.savoris.ai.AiModelClient;
import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KitchenForecastIntegrationTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiModelClient ai;

    @BeforeEach
    void configureForecastModel() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(anyList(), anyList(), anyBoolean())).thenReturn(new AiModelClient.AiCompletion(
            "{\"forecast\":[{\"hour\":\"23h\",\"predictedLoad\":70,\"risk\":\"HIGH\"},"
                + "{\"hour\":\"00h\",\"predictedLoad\":100,\"risk\":\"OVERLOADED\"}],"
                + "\"alert\":\"Theo dõi nguy cơ quá tải\",\"actions\":[\"Theo dõi bếp\"],\"suggestedPauseAt\":null}", List.of()));
    }

    @Test
    void forecastReadsRealOrdersByHanoiWeekdayAndHour() throws Exception {
        BranchEntity branch = createBranch(40);
        StaffEntity manager = createStaff(StaffRole.MANAGER, branch);
        MenuItemEntity dish = createMenuItem("Pho test", 50000);
        String order = mockMvc.perform(post("/orders").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(dish, 1))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int orderId = com.jayway.jsonpath.JsonPath.read(order, "$.id");

        LocalDateTime businessOpenedAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            .minusDays(7).withHour(18).withMinute(10).withSecond(0).withNano(0);
        Instant instant = Instant.now();
        ZoneOffset databaseOffset = ZoneId.systemDefault().getRules().getOffset(instant);
        ZoneOffset businessOffset = ZoneId.of("Asia/Ho_Chi_Minh").getRules().getOffset(instant);
        int shiftMinutes = (businessOffset.getTotalSeconds() - databaseOffset.getTotalSeconds()) / 60;
        jdbc.update("update orders set opened_at=? where id=?", businessOpenedAt.minusMinutes(shiftMinutes), orderId);

        mockMvc.perform(get("/dashboard/branch/" + branch.getId() + "/kitchen-forecast").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentLoadPercent").value(2))
            .andExpect(jsonPath("$.history").isArray())
            .andExpect(jsonPath("$.history", org.hamcrest.Matchers.hasSize(24)))
            .andExpect(jsonPath("$.history[18].hour").value("18h"))
            .andExpect(jsonPath("$.history[18].averageOrders").value(0.3))
            .andExpect(jsonPath("$.history[18].averagePortions").value(0.3))
            .andExpect(jsonPath("$.forecast[0].predictedLoad").value(70));
    }
}
