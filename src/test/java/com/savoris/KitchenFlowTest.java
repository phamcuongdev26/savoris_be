package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KitchenFlowTest extends IntegrationTestSupport {

    private BranchEntity branch;
    private StaffEntity waiter;
    private StaffEntity chef;
    private MenuItemEntity pho;
    private MenuItemEntity rice;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        waiter = createStaff(StaffRole.CASHIER, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        pho = createMenuItem("Pho bo", 55000);
        rice = createMenuItem("Com rang", 45000);
    }

    private String order(String type, Integer table, int[]... dishes) throws Exception {
        return mockMvc.perform(post("/orders").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, type, table, dishes)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void queueShowsDishesFirstComeFirstServed_withOrderInfo() throws Exception {
        String first = order("DINE_IN", table(branch, 3), dish(pho, 2));
        order("TAKEAWAY", null, dish(rice, 1));

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].itemName").value("Pho bo"))
            .andExpect(jsonPath("$[0].quantity").value(2))
            .andExpect(jsonPath("$[0].tableId").value(table(branch, 3)))
            .andExpect(jsonPath("$[0].orderCode").value((String) JsonPath.read(first, "$.code")))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[1].itemName").value("Com rang"))
            .andExpect(jsonPath("$[1].orderType").value("TAKEAWAY"));
    }

    @Test
    void dishMovesThroughKitchen_andLeavesQueueWhenServed() throws Exception {
        int itemId = JsonPath.read(order("DINE_IN", table(branch, 1), dish(pho, 1)), "$.items[0].id");

        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COOKING"))
            .andExpect(jsonPath("$.cookedBy").value(chef.getId()));

        // Cannot skip steps or go back
        mockMvc.perform(put("/kitchen/items/" + itemId + "/served").with(as(chef)))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef)))
            .andExpect(status().isConflict());

        mockMvc.perform(put("/kitchen/items/" + itemId + "/ready").with(as(chef)))
            .andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(put("/kitchen/items/" + itemId + "/served").with(as(chef)))
            .andExpect(jsonPath("$.status").value("SERVED"));

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void whenManyCooksGrabTheSameDishAtOnce_exactlyOneGetsIt() throws Exception {
        int itemId = JsonPath.read(order("DINE_IN", table(branch, 1), dish(pho, 1)), "$.items[0].id");
        List<StaffEntity> cooks = new ArrayList<>();
        for (int i = 0; i < 8; i++) cooks.add(createStaff(StaffRole.CHEF, branch));

        ExecutorService pool = Executors.newFixedThreadPool(cooks.size());
        try {
            List<Callable<Integer>> attempts = cooks.stream()
                .<Callable<Integer>>map(cook -> () -> mockMvc
                    .perform(put("/kitchen/items/" + itemId + "/start").with(as(cook)))
                    .andReturn().getResponse().getStatus())
                .toList();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(attempts)) statuses.add(f.get());

            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(cooks.size() - 1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void cookFromAnotherBranchCannotSeeOrTouchThisKitchen() throws Exception {
        int itemId = JsonPath.read(order("DINE_IN", table(branch, 1), dish(pho, 1)), "$.items[0].id");
        StaffEntity outsider = createStaff(StaffRole.CHEF, createBranch(null));

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(outsider)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(outsider)))
            .andExpect(status().isForbidden());
    }
}
