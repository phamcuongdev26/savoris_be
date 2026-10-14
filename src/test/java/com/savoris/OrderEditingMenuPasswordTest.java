package com.savoris;

import com.savoris.constant.DiscountType;
import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.CustomerEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.PromotionEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.CustomerRepository;
import com.savoris.repository.PromotionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderEditingMenuPasswordTest extends IntegrationTestSupport {

    @Autowired PromotionRepository promotionRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private BranchEntity branch;
    private StaffEntity waiter;
    private StaffEntity cashier;
    private StaffEntity chef;
    private StaffEntity manager;
    private MenuItemEntity pho;
    private MenuItemEntity tea;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        waiter = createStaff(StaffRole.WAITER, branch);
        cashier = createStaff(StaffRole.WAITER, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        manager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        pho = createMenuItem("Pho bo", 50000);
        tea = createMenuItem("Tra da", 5000);
    }

    private String createOrder(String json) throws Exception {
        return mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String addJson(MenuItemEntity item, int quantity) {
        return "{\"items\":[{\"menuItemId\":" + item.getId() + ",\"quantity\":" + quantity + "}]}";
    }

    // ---------------------------------------------------------------- add dishes

    @Test
    void guestsOrderAnotherRound_onTheSameBill() throws Exception {
        String created = createOrder(orderJson(branch, "DINE_IN", table(branch, 1), dish(pho, 1)));
        int orderId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/orders/" + orderId + "/items").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(addJson(tea, 3)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.subtotal").value(65000))
            .andExpect(jsonPath("$.totalAmount").value(65000));

        // The new round goes straight to the kitchen
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(2)));

        // Paid bill: no more additions
        pay(cashier, orderId, 65000).andExpect(status().isCreated());
        mockMvc.perform(post("/orders/" + orderId + "/items").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(addJson(tea, 1)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    @Test
    void addingToATakeawayOrderRespectsTheFullKitchen() throws Exception {
        BranchEntity small = createBranch(2);
        StaffEntity smallCashier = createStaff(StaffRole.WAITER, small);
        String takeaway = mockMvc.perform(post("/orders").with(as(smallCashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(small, "TAKEAWAY", null, dish(pho, 2))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/orders/" + JsonPath.read(takeaway, "$.id") + "/items").with(as(smallCashier))
                .contentType(MediaType.APPLICATION_JSON).content(addJson(tea, 1)))
            .andExpect(status().isServiceUnavailable());
    }

    // ---------------------------------------------------------------- cancel one dish

    @Test
    void dishNotStartedCanBeDroppedByTheFloor_onceCookingOnlyByAManager_servedNever() throws Exception {
        String created = createOrder(orderJson(branch, "DINE_IN", table(branch, 1), dish(pho, 1), dish(tea, 2)));
        int orderId = JsonPath.read(created, "$.id");
        int phoItem = JsonPath.read(created, "$.items[0].id");
        int teaItem = JsonPath.read(created, "$.items[1].id");

        // Tea not started: the cashier drops it, the bill shrinks and the kitchen no longer sees it
        mockMvc.perform(put("/orders/" + orderId + "/items/" + teaItem + "/cancel").with(as(cashier))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"khach doi y\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[1].status").value("CANCELLED"))
            .andExpect(jsonPath("$.items[1].cancelReason").value("khach doi y"))
            .andExpect(jsonPath("$.totalAmount").value(50000));
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(1)));

        // Pho already on the stove: the cashier cannot, the manager can
        mockMvc.perform(put("/kitchen/items/" + phoItem + "/start").with(as(chef))).andExpect(status().isOk());
        mockMvc.perform(put("/orders/" + orderId + "/items/" + phoItem + "/cancel").with(as(cashier)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/orders/" + orderId + "/items/" + phoItem + "/cancel").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(0));

        // Served dish stays on the bill
        String second = createOrder(orderJson(branch, "DINE_IN", table(branch, 2), dish(tea, 1)));
        int secondId = JsonPath.read(second, "$.id");
        int servedItem = JsonPath.read(second, "$.items[0].id");
        for (String step : List.of("start", "ready", "served")) {
            mockMvc.perform(put("/kitchen/items/" + servedItem + "/" + step).with(as(chef))).andExpect(status().isOk());
        }
        mockMvc.perform(put("/orders/" + secondId + "/items/" + servedItem + "/cancel").with(as(manager)))
            .andExpect(status().isConflict());

        // A dish of another order
        mockMvc.perform(put("/orders/" + orderId + "/items/" + servedItem + "/cancel").with(as(manager)))
            .andExpect(status().isNotFound());
    }

    @Test
    void droppingDishesBelowThePromotionMinimum_removesThePromotion_andReturnsExtraPoints() throws Exception {
        PromotionEntity promotion = new PromotionEntity();
        promotion.setCode("MIN100_" + unique());
        promotion.setName("Min 100k");
        promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(BigDecimal.valueOf(10000));
        promotion.setMinOrderAmount(BigDecimal.valueOf(100000));
        promotion.setStartAt(LocalDateTime.now().minusHours(1));
        promotion.setEndAt(LocalDateTime.now().plusDays(1));
        promotion.setUsedCount(0);
        promotion.setIsActive(true);
        promotion = promotionRepository.save(promotion);
        CustomerEntity customer = new CustomerEntity();
        customer.setPhone("0966" + unique());
        customer.setPoints(80);
        customer = customerRepository.save(customer);

        // 2 x 50.000 = 100.000 - 10.000 promo - 80 points (80.000) = 10.000
        String created = createOrder("{\"branchId\":" + branch.getId() + ",\"orderType\":\"TAKEAWAY\",\"customerId\":" + customer.getId()
            + ",\"promotionCode\":\"" + promotion.getCode() + "\",\"pointsToUse\":80,\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":2}]}");
        assertThat((Integer) JsonPath.read(created, "$.totalAmount")).isEqualTo(10000);
        int orderId = JsonPath.read(created, "$.id");

        // Customer changes their mind: one bowl instead of two -> 50.000, promo no longer applies, at most 50 points fit
        int phoItem = JsonPath.read(created, "$.items[0].id");
        mockMvc.perform(put("/orders/" + orderId + "/items/" + phoItem + "/cancel").with(as(cashier)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/orders/" + orderId + "/items").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(addJson(pho, 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotal").value(50000))
            .andExpect(jsonPath("$.discountAmount").value(0))
            .andExpect(jsonPath("$.pointsUsed").value(0))
            .andExpect(jsonPath("$.totalAmount").value(50000));

        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getPoints()).isEqualTo(80);
    }

    // ---------------------------------------------------------------- branch menu

    @Test
    void kitchenMarksADishSoldOut_andTheManagerSetsTheBranchPrice() throws Exception {
        String menuUrl = "/branches/" + branch.getId() + "/menu/" + pho.getId();

        mockMvc.perform(put(menuUrl + "/availability").with(as(chef)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 1))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MENU_ITEM_UNAVAILABLE"));

        mockMvc.perform(put(menuUrl + "/availability").with(as(chef)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
            .andExpect(status().isOk());

        mockMvc.perform(put(menuUrl + "/price").with(as(chef)).contentType(MediaType.APPLICATION_JSON).content("{\"price\":70000}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(menuUrl + "/price").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content("{\"price\":70000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(70000))
            .andExpect(jsonPath("$.basePrice").value(50000));
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 1))))
            .andExpect(jsonPath("$.totalAmount").value(70000));

        mockMvc.perform(put(menuUrl + "/price").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content("{\"price\":null}"))
            .andExpect(jsonPath("$.price").value(50000));

        String menu = mockMvc.perform(get("/branches/" + branch.getId() + "/menu").with(as(waiter)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Boolean> available = JsonPath.read(menu, "$[?(@.menuItemId == " + pho.getId() + ")].available");
        assertThat(available).containsExactly(true);

        StaffEntity otherManager = createStaff(StaffRole.BRANCH_MANAGER, createBranch(null));
        mockMvc.perform(put(menuUrl + "/availability").with(as(otherManager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void managerResetsAForgottenPassword_andOldSessionsEnd() throws Exception {
        cashier.setPasswordHash(passwordEncoder.encode("Old@12345"));
        staffRepository.save(cashier);
        String session = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Old@12345\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(session, "$.refreshToken");

        mockMvc.perform(put("/staff/" + cashier.getId() + "/password").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"New@12345\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/staff/" + cashier.getId() + "/password").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"abcd\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/staff/" + cashier.getId() + "/password").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"New@12345\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Old@12345\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"New@12345\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized());

        // A manager cannot reset another manager or the owner
        StaffEntity otherManager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        mockMvc.perform(put("/staff/" + otherManager.getId() + "/password").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"New@12345\"}"))
            .andExpect(status().isForbidden());
    }
}
