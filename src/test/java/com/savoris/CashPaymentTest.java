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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CashPaymentTest extends IntegrationTestSupport {

    private BranchEntity branch;
    private StaffEntity cashier;
    private StaffEntity manager;
    private MenuItemEntity pho;
    private int orderId;

    @BeforeEach
    void setUp() throws Exception {
        branch = createBranch(null);
        cashier = createStaff(StaffRole.STAFF, branch);
        manager = createStaff(StaffRole.MANAGER, branch);
        pho = createMenuItem("Pho bo", 55000);
        // 2 x 55.000 = 110.000
        String created = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 2))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        orderId = JsonPath.read(created, "$.id");
    }

    @Test
    void cashierEntersCashReceived_andServerWorksOutTheChange() throws Exception {
        pay(cashier, orderId, 200000)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.method").value("CASH"))
            .andExpect(jsonPath("$.amount").value(110000))
            .andExpect(jsonPath("$.cashReceived").value(200000))
            .andExpect(jsonPath("$.changeAmount").value(90000))
            .andExpect(jsonPath("$.receivedBy").value(cashier.getId()));

        mockMvc.perform(get("/payments/order/" + orderId).with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.changeAmount").value(90000));
    }

    @Test
    void onlyCashIsAccepted() throws Exception {
        mockMvc.perform(post("/payments").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"cashReceived\":110000,\"paymentMethod\":\"BANK_TRANSFER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_SUPPORTED"));
    }

    @Test
    void notEnoughCash_isRejected() throws Exception {
        pay(cashier, orderId, 100000)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"));
    }

    @Test
    void anOrderIsPaidOnlyOnce_evenWhenTwoCashiersPressPayAtTheSameTime() throws Exception {
        List<StaffEntity> cashiers = List.of(cashier, createStaff(StaffRole.STAFF, branch),
            createStaff(StaffRole.STAFF, branch), createStaff(StaffRole.STAFF, branch));
        ExecutorService pool = Executors.newFixedThreadPool(cashiers.size());
        try {
            List<Callable<Integer>> attempts = cashiers.stream()
                .<Callable<Integer>>map(c -> () -> pay(c, orderId, 110000).andReturn().getResponse().getStatus())
                .toList();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(attempts)) statuses.add(f.get());
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(cashiers.size() - 1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void paidOrderMustBeRefundedByAManagerBeforeItCanBeCancelled() throws Exception {
        String paid = pay(cashier, orderId, 110000).andReturn().getResponse().getContentAsString();
        int paymentId = JsonPath.read(paid, "$.id");

        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier)))
            .andExpect(status().isConflict());

        // Cashiers cannot give money back on their own
        mockMvc.perform(put("/payments/" + paymentId + "/refund").with(as(cashier)))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/payments/" + paymentId + "/refund").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.refundedBy").value(manager.getId()));
        mockMvc.perform(put("/payments/" + paymentId + "/refund").with(as(manager)))
            .andExpect(status().isConflict());

        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cannotPayForAnotherBranchOrACancelledOrder() throws Exception {
        StaffEntity outsider = createStaff(StaffRole.STAFF, createBranch(null));
        pay(outsider, orderId, 110000).andExpect(status().isForbidden());

        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier))).andExpect(status().isOk());
        pay(cashier, orderId, 110000).andExpect(status().isConflict());

        pay(cashier, 999999, 1000).andExpect(status().isNotFound());
    }
}
