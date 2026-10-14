package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChainDashboardTest extends IntegrationTestSupport {

    @Test
    void ownerSeesEveryBranchAtOnce() throws Exception {
        StaffEntity owner = createStaff(StaffRole.ADMIN, null);
        MenuItemEntity pho = createMenuItem("Pho bo", 50000);

        // Branch A: one finished order (revenue 100k) and one open order
        BranchEntity a = createBranch(null);
        String done = mockMvc.perform(post("/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(a, "TAKEAWAY", null, dish(pho, 2))))
            .andReturn().getResponse().getContentAsString();
        int itemId = JsonPath.read(done, "$.items[0].id");
        int orderId = JsonPath.read(done, "$.id");
        for (String step : List.of("start", "ready", "served")) {
            mockMvc.perform(put("/kitchen/items/" + itemId + "/" + step).with(as(owner))).andExpect(status().isOk());
        }
        pay(owner, orderId, 100000).andExpect(status().isCreated());
        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(owner))).andExpect(status().isOk());
        mockMvc.perform(post("/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(a, "DINE_IN", table(a, 1), dish(pho, 1))));

        // Branch B: kitchen of 2 portions with 3 waiting -> overloaded
        BranchEntity b = createBranch(2);
        mockMvc.perform(post("/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(b, "DINE_IN", table(b, 1), dish(pho, 3))));

        String json = mockMvc.perform(get("/dashboard/chain").with(as(owner)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> branchA = branch(json, a);
        assertThat(branchA.get("openOrders")).isEqualTo(1);
        assertThat(branchA.get("completedOrdersToday")).isEqualTo(1);
        assertThat(((Number) branchA.get("revenueToday")).longValue()).isEqualTo(100000);
        assertThat(JsonPath.<String>read(branchA, "$.kitchen.status")).isEqualTo("NORMAL");

        Map<String, Object> branchB = branch(json, b);
        assertThat(branchB.get("openOrders")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(branchB, "$.kitchen.pendingPortions")).isEqualTo(3);
        assertThat(JsonPath.<String>read(branchB, "$.kitchen.status")).isEqualTo("OVERLOADED");
        assertThat(JsonPath.<Boolean>read(branchB, "$.kitchen.acceptingTakeaway")).isFalse();

        assertThat(JsonPath.<Integer>read(json, "$.overloadedBranches")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void onlyTheChainOwnerCanSeeTheDashboard() throws Exception {
        StaffEntity manager = createStaff(StaffRole.BRANCH_MANAGER, createBranch(null));
        mockMvc.perform(get("/dashboard/chain").with(as(manager))).andExpect(status().isForbidden());
        mockMvc.perform(get("/dashboard/chain")).andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> branch(String json, BranchEntity branch) {
        List<Map<String, Object>> found = JsonPath.read(json, "$.branches[?(@.branchId == " + branch.getId() + ")]");
        assertThat(found).hasSize(1);
        return found.get(0);
    }
}
