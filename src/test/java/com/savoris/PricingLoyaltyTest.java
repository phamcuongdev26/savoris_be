package com.savoris;

import com.savoris.constant.DiscountType;
import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.CustomerEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.PromotionBranchEntity;
import com.savoris.entity.PromotionEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.CustomerRepository;
import com.savoris.repository.PromotionBranchRepository;
import com.savoris.repository.PromotionRepository;
import com.savoris.repository.SettingRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

class PricingLoyaltyTest extends IntegrationTestSupport {

    @Autowired PromotionRepository promotionRepository;
    @Autowired PromotionBranchRepository promotionBranchRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SettingRepository settingRepository;

    private BranchEntity branch;
    private StaffEntity owner;
    private StaffEntity cashier;
    private StaffEntity chef;
    private StaffEntity manager;
    private MenuItemEntity pho;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        owner = createStaff(StaffRole.ADMIN, null);
        cashier = createStaff(StaffRole.WAITER, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        manager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        pho = createMenuItem("Pho bo", 50000);
    }

    // The VAT setting is shared by the whole application context: put it back for the other tests
    @AfterEach
    void resetVat() {
        settingRepository.deleteAll();
    }

    // ---------------------------------------------------------------- VAT

    @Test
    void vatIsAddedOnTopOfTheOrder_andTheOwnerCanChangeTheRate() throws Exception {
        setVat("8");
        // 3 x 50.000 = 150.000, VAT 8% = 12.000
        order(body(3, null, null, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(150000))
            .andExpect(jsonPath("$.vatAmount").value(12000))
            .andExpect(jsonPath("$.totalAmount").value(162000));

        setVat("10");
        order(body(3, null, null, null)).andExpect(jsonPath("$.totalAmount").value(165000));

        mockMvc.perform(put("/settings/vat").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content("{\"percent\":0}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings/vat").with(as(cashier)))
            .andExpect(jsonPath("$.percent").value(10));
    }

    // ---------------------------------------------------------------- promotions

    @Test
    void percentPromotionIsCapped_andVatIsChargedOnTheDiscountedAmount() throws Exception {
        setVat("8");
        promotion("SALE10", DiscountType.PERCENT, 10, 12000L, 0, null, null);
        // 3 x 50.000 = 150.000, 10% = 15.000 capped at 12.000 -> 138.000, VAT 11.040
        order(body(3, null, "sale10", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.discountAmount").value(12000))
            .andExpect(jsonPath("$.vatAmount").value(11040))
            .andExpect(jsonPath("$.totalAmount").value(149040));
    }

    @Test
    void promotionRulesAreEnforced() throws Exception {
        promotion("MIN200", DiscountType.FIXED_AMOUNT, 20000, null, 200000, null, null);
        order(body(1, null, "MIN200", null))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROMOTION_NOT_APPLICABLE"));

        BranchEntity other = createBranch(null);
        promotion("OTHERONLY", DiscountType.FIXED_AMOUNT, 20000, null, 0, null, other);
        order(body(1, null, "OTHERONLY", null)).andExpect(status().isConflict());

        PromotionEntity expired = promotion("OLD", DiscountType.FIXED_AMOUNT, 20000, null, 0, null, null);
        expired.setEndAt(LocalDateTime.now().minusMinutes(1));
        promotionRepository.save(expired);
        order(body(1, null, "OLD", null)).andExpect(status().isConflict());

        order(body(1, null, "NOPE", null)).andExpect(status().isConflict());

        // A fixed discount never makes the bill negative
        promotion("BIG", DiscountType.FIXED_AMOUNT, 999000, null, 0, null, null);
        order(body(1, null, "BIG", null)).andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void limitedCodeIsNeverOverUsed_evenAtPeakHours_andCancellingGivesTheUseBack() throws Exception {
        promotion("FIRST3", DiscountType.FIXED_AMOUNT, 10000, null, 0, 3, null);
        // One cashier at each of 8 branches: a chain-wide code is raced from different branches at once
        List<BranchEntity> branches = new ArrayList<>();
        List<StaffEntity> cashiers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            BranchEntity b = i == 0 ? branch : createBranch(null);
            branches.add(b);
            cashiers.add(i == 0 ? cashier : createStaff(StaffRole.WAITER, b));
        }

        ExecutorService pool = Executors.newFixedThreadPool(cashiers.size());
        List<String> created = new ArrayList<>();
        try {
            List<Callable<String>> attempts = new ArrayList<>();
            for (int i = 0; i < cashiers.size(); i++) {
                StaffEntity c = cashiers.get(i);
                String json = body(1, null, "FIRST3", null).replace("\"branchId\":" + branch.getId(), "\"branchId\":" + branches.get(i).getId());
                attempts.add(() -> {
                    var res = mockMvc.perform(post("/orders").with(as(c)).contentType(MediaType.APPLICATION_JSON)
                        .content(json)).andReturn().getResponse();
                    return res.getStatus() + " " + res.getContentAsString();
                });
            }
            for (Future<String> f : pool.invokeAll(attempts)) created.add(f.get());
        } finally {
            pool.shutdown();
        }
        assertThat(created).filteredOn(r -> r.startsWith("201")).hasSize(3);
        assertThat(created).filteredOn(r -> r.startsWith("409")).hasSize(5);
        assertThat(promotionRepository.findByCodeIgnoreCase("FIRST3").orElseThrow().getUsedCount()).isEqualTo(3);

        int orderId = JsonPath.read(created.stream().filter(r -> r.startsWith("201")).findFirst().orElseThrow().substring(4), "$.id");
        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(owner))).andExpect(status().isOk());
        assertThat(promotionRepository.findByCodeIgnoreCase("FIRST3").orElseThrow().getUsedCount()).isEqualTo(2);
        order(body(1, null, "FIRST3", null)).andExpect(status().isCreated());
    }

    @Test
    void cashierCanTryACodeBeforeOrdering() throws Exception {
        promotion("TRY", DiscountType.PERCENT, 20, null, 0, null, null);
        mockMvc.perform(get("/promotions/check").with(as(cashier))
                .param("code", "try").param("branchId", branch.getId().toString()).param("orderAmount", "80000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").value(16000));
        assertThat(promotionRepository.findByCodeIgnoreCase("TRY").orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void onlyTheOwnerCreatesPromotions() throws Exception {
        String json = "{\"code\":\"NEW" + unique() + "\",\"name\":\"New\",\"discountType\":\"PERCENT\",\"discountValue\":15,"
            + "\"startAt\":\"" + LocalDateTime.now().minusHours(1).withNano(0) + "\",\"endAt\":\"" + LocalDateTime.now().plusDays(7).withNano(0) + "\","
            + "\"branchIds\":[" + branch.getId() + "]}";
        mockMvc.perform(post("/promotions").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/promotions").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.branchIds", hasSize(1)));
        mockMvc.perform(post("/promotions").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"discountValue\":15", "\"discountValue\":150").replace("\"code\":\"NEW", "\"code\":\"X")))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- loyalty points

    @Test
    void completedOrderEarnsPoints_andTheCustomerSpendsThemNextTime() throws Exception {
        CustomerEntity customer = customer(0);

        // 4 x 50.000 = 200.000 -> 20 points
        int orderId = JsonPath.read(order(body(4, customer.getId(), null, null)).andReturn().getResponse().getContentAsString(), "$.id");
        serveAll(orderId);
        pay(cashier, orderId, 200000).andExpect(status().isCreated());
        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(cashier))).andExpect(status().isOk());

        CustomerEntity after = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(after.getPoints()).isEqualTo(20);
        assertThat(after.getTotalSpent()).isEqualByComparingTo("200000");

        // Next visit: 15 points = 15.000 off
        order(body(1, customer.getId(), null, 15))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pointsUsed").value(15))
            .andExpect(jsonPath("$.pointsDiscount").value(15000))
            .andExpect(jsonPath("$.totalAmount").value(35000));
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getPoints()).isEqualTo(5);

        mockMvc.perform(get("/customers/" + customer.getId() + "/points").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].points").value(-15))
            .andExpect(jsonPath("$[1].points").value(20));

        mockMvc.perform(get("/customers/by-phone").param("phone", customer.getPhone()).with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points").value(5));
    }

    @Test
    void pointRulesAreEnforced_andCancellingGivesThePointsBack() throws Exception {
        CustomerEntity customer = customer(30);

        order(body(1, customer.getId(), null, 31))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOT_ENOUGH_POINTS"));
        CustomerEntity rich = customer(500);
        order(body(1, rich.getId(), null, 60))
            .andExpect(status().isBadRequest());  // 60.000 off a 50.000 order
        order(body(1, null, null, 5))
            .andExpect(status().isBadRequest());  // points need a customer

        int orderId = JsonPath.read(order(body(1, customer.getId(), null, 30)).andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getPoints()).isZero();
        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier))).andExpect(status().isOk());
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getPoints()).isEqualTo(30);
    }

    @Test
    void refundingACompletedOrderTakesTheEarnedPointsBack() throws Exception {
        CustomerEntity customer = customer(0);
        int orderId = JsonPath.read(order(body(2, customer.getId(), null, null)).andReturn().getResponse().getContentAsString(), "$.id");
        serveAll(orderId);
        int paymentId = JsonPath.read(pay(cashier, orderId, 100000).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/orders/" + orderId + "/complete").with(as(cashier))).andExpect(status().isOk());
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getPoints()).isEqualTo(10);

        mockMvc.perform(put("/payments/" + paymentId + "/refund").with(as(manager))).andExpect(status().isOk());
        CustomerEntity after = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(after.getPoints()).isZero();
        assertThat(after.getTotalSpent()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- helpers

    private void setVat(String percent) throws Exception {
        mockMvc.perform(put("/settings/vat").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"percent\":" + percent + "}"))
            .andExpect(status().isOk());
    }

    private String body(int quantity, Integer customerId, String promotionCode, Integer points) {
        return "{\"branchId\":" + branch.getId() + ",\"orderType\":\"TAKEAWAY\""
            + (customerId != null ? ",\"customerId\":" + customerId : "")
            + (promotionCode != null ? ",\"promotionCode\":\"" + promotionCode + "\"" : "")
            + (points != null ? ",\"pointsToUse\":" + points : "")
            + ",\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":" + quantity + "}]}";
    }

    private ResultActions order(String body) throws Exception {
        return mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void serveAll(int orderId) throws Exception {
        String order = mockMvc.perform(get("/orders/" + orderId).with(as(cashier))).andReturn().getResponse().getContentAsString();
        List<Integer> itemIds = JsonPath.read(order, "$.items[*].id");
        for (int itemId : itemIds) {
            for (String step : List.of("start", "ready", "served")) {
                mockMvc.perform(put("/kitchen/items/" + itemId + "/" + step).with(as(chef))).andExpect(status().isOk());
            }
        }
    }

    private CustomerEntity customer(int points) {
        CustomerEntity customer = new CustomerEntity();
        customer.setPhone("0977" + unique());
        customer.setFullName("Customer");
        customer.setPoints(points);
        return customerRepository.save(customer);
    }

    private PromotionEntity promotion(String code, DiscountType type, long value, Long maxDiscount, long minOrder,
                                      Integer usageLimit, BranchEntity onlyAt) {
        // Several tests use the same readable code: rename any earlier one out of the way
        promotionRepository.findByCodeIgnoreCase(code).ifPresent(old -> {
            old.setCode(old.getCode() + "_" + unique());
            promotionRepository.save(old);
        });
        PromotionEntity promotion = new PromotionEntity();
        promotion.setCode(code);
        promotion.setName(code);
        promotion.setDiscountType(type);
        promotion.setDiscountValue(BigDecimal.valueOf(value));
        promotion.setMaxDiscount(maxDiscount != null ? BigDecimal.valueOf(maxDiscount) : null);
        promotion.setMinOrderAmount(BigDecimal.valueOf(minOrder));
        promotion.setStartAt(LocalDateTime.now().minusHours(1));
        promotion.setEndAt(LocalDateTime.now().plusDays(1));
        promotion.setUsageLimit(usageLimit);
        promotion.setUsedCount(0);
        promotion.setIsActive(true);
        promotion = promotionRepository.save(promotion);
        if (onlyAt != null) {
            PromotionBranchEntity link = new PromotionBranchEntity();
            link.setPromotionId(promotion.getId());
            link.setBranchId(onlyAt.getId());
            promotionBranchRepository.save(link);
        }
        return promotion;
    }
}
