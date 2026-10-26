package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderFlowTest extends IntegrationTestSupport {

    private BranchEntity branch;
    private StaffEntity cashier;
    private MenuItemEntity pho;
    private MenuItemEntity tea;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        cashier = createStaff(StaffRole.STAFF, branch);
        pho = createMenuItem("Pho bo", 55000);
        tea = createMenuItem("Tra da", 5000);
    }

    @Test
    void createOrder_takesNamesAndPricesFromMenu_andNumbersOrdersPerBranchPerDay() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "B" + branch.getId() + "-" + today + "-";

        // Client tries to sneak in its own price: it must be ignored
        String body = "{\"branchId\":" + branch.getId() + ",\"orderType\":\"DINE_IN\",\"tableId\":" + table(branch, 1) + ","
            + "\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":2,\"unitPrice\":1},"
            + "{\"menuItemId\":" + tea.getId() + ",\"quantity\":3}]}";

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(prefix + "0001"))
            .andExpect(jsonPath("$.staffId").value(cashier.getId()))
            .andExpect(jsonPath("$.items[0].itemName").value("Pho bo"))
            .andExpect(jsonPath("$.items[0].unitPrice").value(55000))
            .andExpect(jsonPath("$.items[0].orderedByGuest").value(false))
            .andExpect(jsonPath("$.items[0].amount").value(110000))
            .andExpect(jsonPath("$.totalAmount").value(125000));

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(tea, 1))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(prefix + "0002"));
    }

    @Test
    void branchCanChangePriceOrMarkDishSoldOut() throws Exception {
        overrideAtBranch(branch, pho, 60000L, true);
        overrideAtBranch(branch, tea, null, false);

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 1))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(60000));

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(tea, 1))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MENU_ITEM_UNAVAILABLE"));
    }

    @Test
    void staffCannotTouchAnotherBranch_butChainAdminCan() throws Exception {
        BranchEntity otherBranch = createBranch(null);
        StaffEntity admin = createStaff(StaffRole.ADMIN, null);

        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(otherBranch, "TAKEAWAY", null, dish(tea, 1))))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/orders").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(otherBranch, "TAKEAWAY", null, dish(tea, 1))))
            .andExpect(status().isCreated());
    }

    @Test
    void invalidOrdersAreRejected() throws Exception {
        // Dine-in without a table
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", null, dish(tea, 1))))
            .andExpect(status().isBadRequest());

        // Zero quantity
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(tea, 0))))
            .andExpect(status().isBadRequest());

        // Unknown dish
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, new int[]{999999, 1})))
            .andExpect(status().isConflict());

        // Unknown order
        mockMvc.perform(get("/orders/999999").with(as(cashier)))
            .andExpect(status().isNotFound());
    }

    @Test
    void orderCanOnlyBeCompletedWhenEveryDishIsServed() throws Exception {
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);
        String created = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 5), dish(pho, 1))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(created, "$.id");
        int itemId = JsonPath.read(created, "$.items[0].id");

        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(cashier)))
            .andExpect(status().isConflict());

        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef))).andExpect(status().isOk());
        mockMvc.perform(put("/kitchen/items/" + itemId + "/ready").with(as(chef))).andExpect(status().isOk());
        mockMvc.perform(put("/kitchen/items/" + itemId + "/served").with(as(chef))).andExpect(status().isOk());

        // Every dish served, but nobody paid yet
        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(cashier)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));

        pay(cashier, orderId, 55000).andExpect(status().isCreated());
        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Closed orders cannot change again
        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier)))
            .andExpect(status().isConflict());
    }

    @Test
    void cancellingAnOrderRemovesItsDishesFromTheKitchenQueue() throws Exception {
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);
        String created = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 2), dish(tea, 1))))
            .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.items[0].status").value("CANCELLED"));

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void openOrdersOfBranchAreListedWithTheirItems() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 1), dish(tea, 2))));
        }
        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[2].items", hasSize(2)));
    }
}
