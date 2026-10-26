package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeakHourTest extends IntegrationTestSupport {

    @Test
    void fullKitchen_pausesTakeawayAndDelivery_butKeepsServingSeatedGuests() throws Exception {
        BranchEntity branch = createBranch(5);
        StaffEntity cashier = createStaff(StaffRole.STAFF, branch);
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);
        MenuItemEntity pho = createMenuItem("Pho bo", 55000);
        MenuItemEntity rice = createMenuItem("Com rang", 45000);

        // 5 portions = 100% of a 5-portion kitchen
        String rush = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(pho, 3), dish(rice, 2))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/load").with(as(chef)))
            .andExpect(jsonPath("$.pendingPortions").value(5))
            .andExpect(jsonPath("$.loadPercent").value(100))
            .andExpect(jsonPath("$.status").value("OVERLOADED"))
            .andExpect(jsonPath("$.acceptingTakeaway").value(false))
            // Nothing cooked yet: no guess rather than a wrong one
            .andExpect(jsonPath("$.estimatedWaitMinutes").doesNotExist());

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(rice, 1))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BRANCH_BUSY"));
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DELIVERY", null, dish(rice, 1))))
            .andExpect(status().isServiceUnavailable());

        // Guests at a table are still served
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 2), dish(rice, 1))))
            .andExpect(status().isCreated());

        // Kitchen finishes the rush order: 5 portions done, only the 1 new portion left
        for (String path : List.of("$.items[0].id", "$.items[1].id")) {
            int itemId = JsonPath.read(rush, path);
            mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef))).andExpect(status().isOk());
            mockMvc.perform(put("/kitchen/items/" + itemId + "/ready").with(as(chef))).andExpect(status().isOk());
        }

        // Speed = 5 portions per 5 minutes (shortest sample) -> 1 portion waiting = 1 minute
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/load").with(as(chef)))
            .andExpect(jsonPath("$.pendingPortions").value(1))
            .andExpect(jsonPath("$.loadPercent").value(20))
            .andExpect(jsonPath("$.status").value("NORMAL"))
            .andExpect(jsonPath("$.portionsReadyLast30Min").value(5))
            .andExpect(jsonPath("$.estimatedWaitMinutes").value(1))
            .andExpect(jsonPath("$.acceptingTakeaway").value(true));

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(rice, 1))))
            .andExpect(status().isCreated());
    }

    @Test
    void ninebranchesTakingOrdersAtTheSameTime_noErrorsAndNoDuplicateOrderCodes() throws Exception {
        int branchCount = 9;
        int ordersPerBranch = 30;
        MenuItemEntity pho = createMenuItem("Pho bo", 55000);
        List<BranchEntity> branches = new ArrayList<>();
        List<StaffEntity> cashiers = new ArrayList<>();
        for (int i = 0; i < branchCount; i++) {
            BranchEntity branch = createBranch(null);
            branches.add(branch);
            cashiers.add(createStaff(StaffRole.STAFF, branch));
        }

        List<Callable<String>> requests = new ArrayList<>();
        for (int n = 0; n < ordersPerBranch; n++) {
            for (int b = 0; b < branchCount; b++) {
                BranchEntity branch = branches.get(b);
                StaffEntity cashier = cashiers.get(b);
                requests.add(() -> mockMvc.perform(post("/orders").with(as(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(pho, 1))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<String> codes = new ArrayList<>();
        try {
            for (Future<String> f : pool.invokeAll(requests)) {
                codes.add(JsonPath.read(f.get(), "$.code"));
            }
        } finally {
            pool.shutdown();
        }

        assertThat(codes).hasSize(branchCount * ordersPerBranch);
        assertThat(new HashSet<>(codes)).hasSize(codes.size());

        // Every branch numbered its orders 0001..0030 with no gaps
        Map<String, Set<String>> numbersByBranch = codes.stream().collect(Collectors.groupingBy(
            code -> code.substring(0, code.indexOf('-')),
            Collectors.mapping(code -> code.substring(code.lastIndexOf('-') + 1), Collectors.toSet())));
        Set<String> expected = IntStream.rangeClosed(1, ordersPerBranch)
            .mapToObj(i -> "%04d".formatted(i)).collect(Collectors.toSet());
        assertThat(numbersByBranch).hasSize(branchCount);
        numbersByBranch.values().forEach(numbers -> assertThat(numbers).isEqualTo(expected));
    }
}
