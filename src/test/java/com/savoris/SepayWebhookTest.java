package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.payment.Sepay;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "savoris.sepay.api-key=test-sepay-key")
class SepayWebhookTest extends IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired com.savoris.repository.OrderRepository orderRepository;

    private static final String ACCOUNT = "0123456789";

    private BranchEntity branch;
    private StaffEntity chef;
    private StaffEntity cashier;
    private StaffEntity admin;
    private MenuItemEntity pho;

    @BeforeEach
    void setUp() throws Exception {
        branch = createBranch(null);
        chef = createStaff(StaffRole.CHEF, branch);
        cashier = createStaff(StaffRole.STAFF, branch);
        admin = createStaff(StaffRole.ADMIN, null);
        pho = createMenuItem("Pho bo", 50000);
        setBankAccount("MBBank", ACCOUNT);
    }

    // Settings are shared by every test class: leave the chain without a bank account
    @AfterEach
    void tearDown() throws Exception {
        setBankAccount("", "");
    }

    @Test
    void eachOrderGetsItsOwnQrAndTheBankConfirmsThePayment() throws Exception {
        String placed = order().andExpect(status().isCreated())
            .andExpect(jsonPath("$.autoConfirm").value(true))
            .andExpect(jsonPath("$.bankAccount.accountNumber").value(ACCOUNT))
            .andExpect(jsonPath("$.transferQrUrl", containsString("https://qr.sepay.vn/img?acc=" + ACCOUNT + "&bank=MBBank&amount=")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(placed, "$.token");
        String code = JsonPath.read(placed, "$.code");
        long total = ((Number) JsonPath.read(placed, "$.totalAmount")).longValue();

        // The bank app dropped the dashes and glued words around the code
        webhook("test-sepay-key", 1, "in", total, "MBVCB.123.THANHTOAN" + code.replace("-", "") + ".CT tu 0987")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.order").value(code));

        mockMvc.perform(get("/public/takeaway-orders/" + token))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.paid").value(true));
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(1)));

        // SePay retrying the same transaction pays nothing twice
        webhook("test-sepay-key", 1, "in", total, code).andExpect(status().isOk()).andExpect(jsonPath("$.order").doesNotExist());
    }

    @Test
    void onlyGenuineFullTransfersIntoTheChainAccountPayAnOrder() throws Exception {
        String placed = order().andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(placed, "$.token");
        String code = JsonPath.read(placed, "$.code");
        long total = ((Number) JsonPath.read(placed, "$.totalAmount")).longValue();

        webhook("wrong-key", 2, "in", total, code).andExpect(status().isUnauthorized());
        webhook(null, 3, "in", total, code).andExpect(status().isUnauthorized());
        webhook("test-sepay-key", 4, "in", total - 1000, code).andExpect(jsonPath("$.order").doesNotExist());
        webhook("test-sepay-key", 5, "out", total, code).andExpect(jsonPath("$.order").doesNotExist());
        mockMvc.perform(post("/public/sepay/webhook").header("Authorization", "Apikey test-sepay-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":6,\"accountNumber\":\"999999\",\"transferType\":\"in\",\"transferAmount\":" + total + ",\"content\":\"" + code + "\"}"))
            .andExpect(jsonPath("$.order").doesNotExist());

        mockMvc.perform(get("/public/takeaway-orders/" + token))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.paid").value(false));
    }

    @Test
    void cancelledTransferOrdersWaitForTheCashierToRefund() throws Exception {
        // 1. Money arrives after the order was cancelled
        String late = order().andReturn().getResponse().getContentAsString();
        String lateCode = JsonPath.read(late, "$.code");
        long total = ((Number) JsonPath.read(late, "$.totalAmount")).longValue();
        int lateId = orderId(lateCode);
        mockMvc.perform(put("/orders/" + lateId + "/cancel").with(as(cashier))).andExpect(status().isOk());
        webhook("test-sepay-key", 10, "in", total, lateCode).andExpect(jsonPath("$.order").doesNotExist());

        // 2. The guest said they transferred, then the cashier cancelled anyway
        String claimed = order().andReturn().getResponse().getContentAsString();
        String claimedToken = JsonPath.read(claimed, "$.token");
        int claimedId = orderId(JsonPath.read(claimed, "$.code"));
        mockMvc.perform(post("/public/takeaway-orders/" + claimedToken + "/transferred")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(put("/orders/" + claimedId + "/cancel").with(as(cashier))).andExpect(status().isOk());

        // A plain cancelled cash order owes nothing
        String cash = mockMvc.perform(post("/public/takeaway-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"guestName\":\"Lan\",\"guestPhone\":\"0912345678\","
                    + "\"payMethod\":\"CASH\",\"pickupAt\":\"" + takeawayPickupAt() + "\",\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":1}]}"))
            .andReturn().getResponse().getContentAsString();
        mockMvc.perform(put("/orders/" + orderId(JsonPath.read(cash, "$.code")) + "/cancel").with(as(cashier))).andExpect(status().isOk());

        mockMvc.perform(get("/orders/branch/" + branch.getId() + "/refunds-due").with(as(cashier)))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(lateId))
            .andExpect(jsonPath("$[0].guestPhone").value("0912345678"))
            .andExpect(jsonPath("$[1].id").value(claimedId));

        mockMvc.perform(put("/orders/" + lateId + "/refund-settled").with(as(cashier))).andExpect(status().isOk());
        mockMvc.perform(put("/orders/" + lateId + "/refund-settled").with(as(cashier))).andExpect(status().isConflict());
        mockMvc.perform(get("/orders/branch/" + branch.getId() + "/refunds-due").with(as(cashier)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(claimedId));
    }

    @Test
    void findsOrderCodesHoweverTheBankWroteThem() {
        assertThat(Sepay.orderCodesIn("B1-261010-0005")).containsExactly("B1-261010-0005");
        assertThat(Sepay.orderCodesIn("b12 261010 0042 chuyen tien")).containsExactly("B12-261010-0042");
        assertThat(Sepay.orderCodesIn("THANHTOANB12610100005CHUYENKHOAN")).containsExactly("B1-261010-0005");
        assertThat(Sepay.orderCodesIn("B1-261010-00051")).isEmpty();
        assertThat(Sepay.orderCodesIn(null)).isEmpty();
        assertThat(Sepay.qrUrl(new com.savoris.service.SettingService.BankAccount("MBBank", ACCOUNT, null),
            new BigDecimal("108000"), "B1-261010-0005"))
            .isEqualTo("https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=108000&des=B1-261010-0005&template=compact");
    }

    @Test
    void ownerSeesWhetherAutoConfirmIsOn() throws Exception {
        mockMvc.perform(get("/settings/branches/" + branch.getId() + "/bank-account").with(as(admin)))
            .andExpect(jsonPath("$.bank").value("MBBank"))
            .andExpect(jsonPath("$.autoConfirm").value(true))
            .andExpect(jsonPath("$.webhookPath").value("/api/v1/public/sepay/webhook"));
        mockMvc.perform(put("/settings/branches/" + branch.getId() + "/bank-account").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"MBBank\",\"accountNumber\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    private int orderId(String code) {
        return orderRepository.findByCode(code).orElseThrow().getId();
    }

    private ResultActions order() throws Exception {
        return mockMvc.perform(post("/public/takeaway-orders").contentType(MediaType.APPLICATION_JSON)
            .content("{\"branchId\":" + branch.getId() + ",\"guestName\":\"Lan\",\"guestPhone\":\"0912345678\","
                + "\"payMethod\":\"BANK_TRANSFER\",\"pickupAt\":\"" + takeawayPickupAt() + "\",\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":2}]}"));
    }

    private ResultActions webhook(String key, long id, String type, long amount, String content) throws Exception {
        var request = post("/public/sepay/webhook").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + id + ",\"gateway\":\"MBBank\",\"accountNumber\":\"" + ACCOUNT + "\",\"transferType\":\"" + type
                + "\",\"transferAmount\":" + amount + ",\"content\":\"" + content + "\",\"referenceCode\":\"FT" + id + "\"}");
        return mockMvc.perform(key == null ? request : request.header("Authorization", "Apikey " + key));
    }

    private void setBankAccount(String bank, String number) throws Exception {
        mockMvc.perform(put("/settings/branches/" + branch.getId() + "/bank-account").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"" + bank + "\",\"accountNumber\":\"" + number + "\",\"accountName\":\"SAVORIS\"}"))
            .andExpect(status().isOk());
    }
}
