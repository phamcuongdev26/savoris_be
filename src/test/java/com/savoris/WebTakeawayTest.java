package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;


import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebTakeawayTest extends IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @org.springframework.beans.factory.annotation.Autowired com.savoris.service.OrderService orderService;

    private static final String QR = "https://example.com/my-bank-qr.png";

    private BranchEntity branch;
    private StaffEntity cashier;
    private StaffEntity chef;
    private StaffEntity admin;
    private MenuItemEntity pho;

    @BeforeEach
    void setUp() throws Exception {
        branch = createBranch(null);
        cashier = createStaff(StaffRole.STAFF, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        admin = createStaff(StaffRole.ADMIN, null);
        pho = createMenuItem("Pho bo", 50000);
        setTransferQr(QR);
    }

    @Test
    void cashOrderWaitsOutsideTheKitchenUntilTheCashierTakesTheMoney() throws Exception {
        String placed = order("CASH").andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.pickupAt").exists())
            .andExpect(jsonPath("$.paid").value(false))
            .andExpect(jsonPath("$.transferQrUrl").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(placed, "$.token");

        // Not cooked, not counted in the kitchen load, but the cashier sees it with the guest's name
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/load").with(as(chef)))
            .andExpect(jsonPath("$.pendingPortions").value(0));
        String orders = mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(cashier)))
            .andExpect(jsonPath("$[0].status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$[0].guestName").value("Lan"))
            .andExpect(jsonPath("$[0].payMethod").value("CASH"))
            .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(orders, "$[0].id");
        int itemId = JsonPath.read(orders, "$[0].items[0].id");
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef))).andExpect(status().is4xxClientError());

        pay(cashier, orderId, 200000).andExpect(status().isCreated());

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/public/takeaway-orders/" + token))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.paid").value(true));
    }

    @Test
    void transferShowsTheChainQrAndTheCashierConfirmsIt() throws Exception {
        String placed = order("BANK_TRANSFER").andExpect(status().isCreated())
            .andExpect(jsonPath("$.transferQrUrl").value(QR))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(placed, "$.token");
        int orderId = JsonPath.read(mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(cashier)))
            .andReturn().getResponse().getContentAsString(), "$[0].id");

        mockMvc.perform(post("/payments").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"paymentMethod\":\"BANK_TRANSFER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.method").value("BANK_TRANSFER"));

        mockMvc.perform(get("/public/takeaway-orders/" + token))
            .andExpect(jsonPath("$.paid").value(true))
            .andExpect(jsonPath("$.transferQrUrl").doesNotExist());
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef)))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void transferIsOnlyForWebTakeawayAndOnlyOnceTheOwnerSetUpTheQr() throws Exception {
        // A bill taken at the counter stays cash only
        String counter = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "TAKEAWAY", null, dish(pho, 1))))
            .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/payments").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + JsonPath.read(counter, "$.id") + ",\"paymentMethod\":\"BANK_TRANSFER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_SUPPORTED"));

        setTransferQr("");
        order("BANK_TRANSFER").andExpect(status().isBadRequest());
        order("CASH").andExpect(status().isCreated());
    }

    @Test
    void cashierCanCancelAnOrderThatWasNeverPaid() throws Exception {
        order("CASH").andExpect(status().isCreated());
        int orderId = JsonPath.read(mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(cashier)))
            .andReturn().getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(put("/orders/" + orderId + "/cancel").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void guestsSeeBranchesAndTheBranchMenuWithoutLoggingIn() throws Exception {
        overrideAtBranch(branch, pho, 45000L, true);
        // The owner places the branch on the map; guests get the position to find the nearest one
        mockMvc.perform(put("/branches/" + branch.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"address\":\"12 Ly Quoc Su Ha Noi\",\"city\":\"Ha Noi\",\"phone\":\"0901234567\",\"email\":\"branch@test.com\",\"latitude\":21.0307,\"longitude\":105.8489}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latitude").value(21.0307));
        mockMvc.perform(put("/branches/" + branch.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"address\":\"x\",\"city\":\"Ha Noi\",\"latitude\":121}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/public/branches"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + branch.getId() + ")].acceptingTakeaway").value(true))
            .andExpect(jsonPath("$[?(@.id == " + branch.getId() + ")].latitude").value(21.0307))
            .andExpect(jsonPath("$[?(@.id == " + branch.getId() + ")].longitude").value(105.8489));
        mockMvc.perform(get("/public/branches/" + branch.getId() + "/menu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.menu[?(@.menuItemId == " + pho.getId() + ")].price").value(45000));
        mockMvc.perform(get("/public/takeaway-orders/not-a-token")).andExpect(status().isNotFound());
    }

    @Test
    void ownerUploadsTheQrImageAndGuestsSeeIt() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 'I', 'H', 'D', 'R'};
        String saved = mockMvc.perform(multipart(HttpMethod.PUT, "/settings/branches/" + branch.getId() + "/transfer-qr/image").file(new MockMultipartFile("file", "qr.png", "image/png", png)).with(as(admin)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(saved, "$.url");
        org.assertj.core.api.Assertions.assertThat(url).startsWith("/api/v1/public/images/transfer-qr/" + branch.getId() + "?v=");

        // Guests get the bytes without logging in, and a transfer order shows that link
        mockMvc.perform(get("/public/images/transfer-qr/" + branch.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(content().bytes(png));
        order("BANK_TRANSFER").andExpect(status().isCreated()).andExpect(jsonPath("$.transferQrUrl").value(url));

        // Saving the settings form unchanged keeps the uploaded image
        mockMvc.perform(put("/settings/branches/" + branch.getId() + "/transfer-qr").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isOk());

        // Anything that is not really an image is refused, whatever its name; staff other than the owner cannot upload
        mockMvc.perform(multipart(HttpMethod.PUT, "/settings/branches/" + branch.getId() + "/transfer-qr/image").file(new MockMultipartFile("file", "qr.png", "image/png", "<svg/>".getBytes())).with(as(admin)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/settings/branches/" + branch.getId() + "/transfer-qr/image").file(new MockMultipartFile("file", "qr.png", "image/png", png)).with(as(cashier)))
            .andExpect(status().isForbidden());
    }

    @Test
    void unpaidWebTakeawayExpiresUnlessTheGuestSaidTheyTransferred() throws Exception {
        String claimedToken = JsonPath.read(order("BANK_TRANSFER").andReturn().getResponse().getContentAsString(), "$.token");
        mockMvc.perform(post("/public/takeaway-orders/" + claimedToken + "/transferred"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferClaimed").value(true));
        String forgottenToken = JsonPath.read(order("CASH").andReturn().getResponse().getContentAsString(), "$.token");

        // Both placed 31 minutes ago
        jdbcTemplate.update("update orders set opened_at = now() - interval '31 minutes' where public_token in (?, ?)", claimedToken, forgottenToken);
        orderService.cancelExpiredWebTakeaways(30);

        mockMvc.perform(get("/public/takeaway-orders/" + forgottenToken))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("Quá 30 phút chưa thanh toán"));
        mockMvc.perform(get("/public/takeaway-orders/" + claimedToken))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        // The cashier sees which unpaid orders the guest claims to have paid
        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(cashier)))
            .andExpect(jsonPath("$[0].transferClaimedAt").exists());
    }

    @Test
    void guestsSubscribeToPushOnlyWithARealPushService() throws Exception {
        String token = JsonPath.read(order("CASH").andReturn().getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/public/push/public-key")).andExpect(jsonPath("$.publicKey").isString());
        mockMvc.perform(post("/public/takeaway-orders/" + token + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\",\"keys\":{\"p256dh\":\"BNcR\",\"auth\":\"tBH\"}}"))
            .andExpect(status().isNoContent());
        // Never let the server be pointed at an arbitrary address
        mockMvc.perform(post("/public/takeaway-orders/" + token + "/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"http://127.0.0.1:8080/api/v1/orders\",\"keys\":{\"p256dh\":\"BNcR\",\"auth\":\"tBH\"}}"))
            .andExpect(status().isBadRequest());
    }

    private ResultActions order(String payMethod) throws Exception {
        return mockMvc.perform(post("/public/takeaway-orders").contentType(MediaType.APPLICATION_JSON)
            .content("{\"branchId\":" + branch.getId() + ",\"guestName\":\"Lan\",\"guestPhone\":\"0912345678\","
                + "\"payMethod\":\"" + payMethod + "\",\"pickupAt\":\"" + takeawayPickupAt() + "\",\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":2}]}"));
    }

    private void setTransferQr(String url) throws Exception {
        mockMvc.perform(put("/settings/branches/" + branch.getId() + "/transfer-qr").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isOk());
    }
}
