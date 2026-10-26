package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.AuditLogEntity;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.AuditLogRepository;
import com.savoris.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRulesTest extends IntegrationTestSupport {

    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuditLogRepository auditLogRepository;

    private BranchEntity branch;
    private StaffEntity owner;
    private StaffEntity manager;
    private StaffEntity waiter;
    private StaffEntity cashier;
    private StaffEntity chef;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        owner = createStaff(StaffRole.ADMIN, null);
        manager = createStaff(StaffRole.MANAGER, branch);
        waiter = createStaff(StaffRole.STAFF, branch);
        cashier = createStaff(StaffRole.STAFF, branch);
        chef = createStaff(StaffRole.CHEF, branch);
    }

    private String staffJson(String role, BranchEntity at) {
        int n = unique();
        String branchId = "ADMIN".equals(role) ? "null" : String.valueOf(at.getId());
        return "{\"code\":\"N" + n + "\",\"fullName\":\"New " + n + "\",\"username\":\"new" + n + "\","
            + "\"email\":\"new" + n + "@savoris.vn\",\"phone\":\"0900" + n + "\",\"role\":\"" + role + "\","
            + "\"branchId\":" + branchId + ",\"hourlyRate\":30000,\"password\":\"Welcome@123\"}";
    }

    @Test
    void waiterCannotCreateAccounts_letAloneAnAdmin() throws Exception {
        mockMvc.perform(post("/staff").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("ADMIN", branch)))
            .andExpect(status().isForbidden());
    }

    @Test
    void managerCanReactivateStaffOnlyInTheirOwnBranch() throws Exception {
        waiter.setStatus(StaffStatus.LOCKED);
        staffRepository.save(waiter);
        StaffEntity otherBranchStaff = createStaff(StaffRole.STAFF, createBranch(null));
        otherBranchStaff.setStatus(StaffStatus.LOCKED);
        staffRepository.save(otherBranchStaff);

        mockMvc.perform(put("/staff/" + waiter.getId() + "/activate").with(as(manager)))
            .andExpect(status().isNoContent());
        assertTrue(staffRepository.findById(waiter.getId()).orElseThrow().getStatus() == StaffStatus.ACTIVE);

        mockMvc.perform(put("/staff/" + otherBranchStaff.getId() + "/activate").with(as(manager)))
            .andExpect(status().isForbidden());
        assertTrue(staffRepository.findById(otherBranchStaff.getId()).orElseThrow().getStatus() == StaffStatus.LOCKED);
    }

    @Test
    void managerHiresOnlyFloorAndKitchenStaff_forTheirOwnBranch() throws Exception {
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("STAFF", branch)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("ADMIN", branch)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("MANAGER", branch)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("STAFF", createBranch(null))))
            .andExpect(status().isForbidden());

        // The chain owner may create anyone
        mockMvc.perform(post("/staff").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("MANAGER", branch)))
            .andExpect(status().isCreated());
    }

    @Test
    void restaurantRouterMatchesThePermissionMatrix() throws Exception {
        MenuItemEntity dish = createMenuItem("Pho " + unique(), 50000);

        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(chef)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/orders").with(as(chef)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(dish, 1))))
            .andExpect(status().isForbidden());

        String orderResp = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(dish, 1))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int itemId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(orderResp).path("items").get(0).path("id").asInt();

        // MANAGER has kitchen access (KITCHEN role now includes MANAGER)
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(manager)))
            .andExpect(status().isOk());
        // STAFF has no kitchen access (blocked by @PreAuthorize before state check)
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(cashier)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/kitchen/items/" + itemId + "/ready").with(as(cashier)))
            .andExpect(status().isForbidden());
    }

    @Test
    void readEndpointsFollowFrontOfHouseKitchenAndManagementRoles() throws Exception {
        int branchId = branch.getId();
        int tableId = table(branch, 1);
        MenuItemEntity dish = createMenuItem("Payment permission " + unique(), 50000);
        String orderResponse = mockMvc.perform(post("/orders").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", tableId, dish(dish, 1))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int orderId = com.jayway.jsonpath.JsonPath.read(orderResponse, "$.id");
        String paymentResponse = pay(waiter, orderId, 50000)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int paymentId = com.jayway.jsonpath.JsonPath.read(paymentResponse, "$.id");

        // STAFF can use floor, order, and reservation reads, but cannot inspect kitchen queues.
        mockMvc.perform(get("/branches/" + branchId + "/tables").with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + branchId + "/areas").with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/orders/branch/" + branchId).with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reservations/branch/" + branchId).with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches").with(as(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(branchId))
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].address").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].managerId").doesNotExist())
            .andExpect(jsonPath("$[0].kitchenCapacity").doesNotExist());
        mockMvc.perform(get("/branches/search?q=Test").with(as(waiter)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/" + paymentId).with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/payments/order/" + orderId).with(as(waiter)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/queue").with(as(waiter)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/void-queue").with(as(waiter)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/load").with(as(waiter)))
            .andExpect(status().isForbidden());

        // CHEF can inspect kitchen data, not customer/order/floor-management data.
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/queue").with(as(chef)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/void-queue").with(as(chef)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/load").with(as(chef)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + branchId + "/tables").with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/branches/" + branchId + "/areas").with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/orders/branch/" + branchId).with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/orders/999999").with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/reservations/branch/" + branchId).with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/branches").with(as(chef)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(branchId))
            .andExpect(jsonPath("$[0].address").doesNotExist())
            .andExpect(jsonPath("$[0].phone").doesNotExist())
            .andExpect(jsonPath("$[0].email").doesNotExist())
            .andExpect(jsonPath("$[0].managerId").doesNotExist())
            .andExpect(jsonPath("$[0].kitchenCapacity").doesNotExist());
        mockMvc.perform(get("/branches/search?q=Test").with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/reservations/999999").with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/" + paymentId).with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/payments/order/" + orderId).with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff/" + waiter.getId()).with(as(chef)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/branches/" + branchId).with(as(chef)))
            .andExpect(status().isForbidden());

        // Managers and the owner retain their documented operational reads.
        mockMvc.perform(get("/branches/" + branchId + "/areas").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].address").value(branch.getAddress()));
        mockMvc.perform(get("/branches/search?q=Test").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + branchId + "/tables").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/orders/branch/" + branchId).with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reservations/branch/" + branchId).with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/queue").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/void-queue").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + branchId + "/load").with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + branchId).with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/staff/" + waiter.getId()).with(as(manager)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + branchId + "/tables").with(as(owner)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reservations/branch/" + branchId).with(as(owner)))
            .andExpect(status().isOk());
    }

    @Test
    void staffCreationIsWrittenToTheAuditLog() throws Exception {
        String body = staffJson("STAFF", branch);
        String username = body.replaceAll(".*\"username\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        List<AuditLogEntity> logs = List.of();
        for (int attempt = 0; attempt < 30; attempt++) {
            logs = auditLogRepository.findAll().stream()
                .filter(log -> log.getNewValue() != null
                    && username.equals(log.getNewValue().path("username").asText()))
                .toList();
            if (!logs.isEmpty()) break;
            Thread.sleep(50);
        }
        assertTrue(!logs.isEmpty(), "Staff creation should be persisted in the audit log");
        assertTrue(logs.get(0).getNewValue().path("role").asText().equals("STAFF"));
    }

    @Test
    void newStaffLogInWithThePasswordTheirManagerSet_notAHardCodedDefault() throws Exception {
        String body = staffJson("CHEF", branch);
        String username = body.replaceAll(".*\"username\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"DefaultPassword@123\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"Welcome@123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void managerCannotPromoteStaff() throws Exception {
        StaffEntity cashier = createStaff(StaffRole.STAFF, branch);
        String promote = "{\"fullName\":\"X\",\"email\":\"x" + unique() + "@savoris.vn\",\"phone\":\"0900000000\","
            + "\"role\":\"MANAGER\",\"branchId\":" + branch.getId() + ",\"hourlyRate\":1}";
        mockMvc.perform(put("/staff/" + cashier.getId()).with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(promote))
            .andExpect(status().isForbidden());
        // ...and cannot touch the owner's account
        mockMvc.perform(delete("/staff/" + owner.getId()).with(as(manager)))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheOwnerChangesBranchesAndTheChainMenu() throws Exception {
        mockMvc.perform(delete("/branches/" + branch.getId()).with(as(waiter))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/branches/" + branch.getId()).with(as(manager))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/menu-items/1").with(as(manager))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/branches/" + branch.getId()).with(as(owner))).andExpect(status().isNoContent());
    }

    @Test
    void managersCanEditOwnBranchButNotOtherBranches() throws Exception {
        String update = "{\"name\":\"Renamed\",\"address\":\"1 Long Street Hà Nội\",\"city\":\"Hà Nội\",\"phone\":\"0901234567\",\"email\":\"branch@test.com\",\"kitchenCapacity\":30}";
        mockMvc.perform(put("/branches/" + branch.getId()).with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk());
        BranchEntity other = createBranch(null);
        mockMvc.perform(put("/branches/" + other.getId()).with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/branches/" + branch.getId()).with(as(waiter)).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isForbidden());
    }

    @Test
    void resignedStaffAreLockedOutImmediately_evenWithAValidToken() throws Exception {
        String token = jwtService.generateAccessToken(waiter);
        mockMvc.perform(get("/branches").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        waiter.setStatus(StaffStatus.RESIGNED);
        staffRepository.save(waiter);
        mockMvc.perform(get("/branches").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void commonMistakesGiveClearErrorsInsteadOf500() throws Exception {
        mockMvc.perform(get("/no-such-endpoint").with(as(owner)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/branches/999999").with(as(owner)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
        mockMvc.perform(get("/branches/abc").with(as(owner)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"abc\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/orders/1").with(as(owner)))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void fiveWrongPasswordsBlockFurtherGuessesFromThatAddress() throws Exception {
        StaffEntity cashier = createStaff(StaffRole.STAFF, branch);
        cashier.setPasswordHash(passwordEncoder.encode("Right@123"));
        staffRepository.save(cashier);
        String wrong = "{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Wrong@123\"}";
        String right = "{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Right@123\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while blocked
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
        // Someone at another address is not affected
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(right)
                .with(request -> { request.setRemoteAddr("10.0.0.99"); return request; }))
            .andExpect(status().isOk());
    }

    @Test
    void staffAndChefAreScopedToTheirOwnBranch_adminSeesAll() throws Exception {
        BranchEntity otherBranch = createBranch(null);
        MenuItemEntity dish = createMenuItem("Bun bo " + unique(), 60000);

        // MANAGER of branch1 cannot read orders or kitchen queue of otherBranch
        mockMvc.perform(get("/orders/branch/" + otherBranch.getId()).with(as(manager)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/kitchen/branches/" + otherBranch.getId() + "/queue").with(as(manager)))
            .andExpect(status().isForbidden());

        // STAFF of branch1 cannot read orders of otherBranch
        mockMvc.perform(get("/orders/branch/" + otherBranch.getId()).with(as(waiter)))
            .andExpect(status().isForbidden());

        // STAFF of branch1 cannot place an order for otherBranch
        mockMvc.perform(post("/orders").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(otherBranch, "DINE_IN", table(otherBranch, 1), dish(dish, 1))))
            .andExpect(status().isForbidden());

        // CHEF of branch1 cannot see the kitchen queue of otherBranch
        mockMvc.perform(get("/kitchen/branches/" + otherBranch.getId() + "/queue").with(as(chef)))
            .andExpect(status().isForbidden());

        // ADMIN (chain owner) can read orders and kitchen queue for any branch
        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(owner)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/orders/branch/" + otherBranch.getId()).with(as(owner)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/kitchen/branches/" + otherBranch.getId() + "/queue").with(as(owner)))
            .andExpect(status().isOk());
    }

    @Test
    void permissionMatrixForWriteEndpoints() throws Exception {
        MenuItemEntity dish = createMenuItem("Perm " + unique(), 40000);
        overrideAtBranch(branch, dish, null, true);

        // Kitchen ops: MANAGER allowed (→ 404 item not found), STAFF blocked (→ 403)
        mockMvc.perform(put("/kitchen/items/999999/start").with(as(manager)))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/kitchen/items/999999/start").with(as(waiter)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/kitchen/items/999999/start").with(as(chef)))
            .andExpect(status().isNotFound());

        // Mark sold out: STAFF blocked (403), CHEF and MANAGER allowed (200)
        mockMvc.perform(put("/branches/" + branch.getId() + "/menu/" + dish.getId() + "/availability")
                .with(as(waiter)).contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/branches/" + branch.getId() + "/menu/" + dish.getId() + "/availability")
                .with(as(chef)).contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/branches/" + branch.getId() + "/menu/" + dish.getId() + "/availability")
                .with(as(manager)).contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
            .andExpect(status().isOk());

        // Cross-branch: MANAGER of branch cannot mark availability on another branch
        BranchEntity other = createBranch(null);
        overrideAtBranch(other, dish, null, true);
        mockMvc.perform(put("/branches/" + other.getId() + "/menu/" + dish.getId() + "/availability")
                .with(as(manager)).contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void wrongGuessesAreForgottenAfterASuccessfulLogin() throws Exception {
        StaffEntity cashier = createStaff(StaffRole.STAFF, branch);
        cashier.setPasswordHash(passwordEncoder.encode("Right@123"));
        staffRepository.save(cashier);
        String wrong = "{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Wrong@123\"}";
        String right = "{\"username\":\"" + cashier.getUsername() + "\",\"password\":\"Right@123\"}";

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong));
            }
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
                .andExpect(status().isOk());
        }
    }
}
