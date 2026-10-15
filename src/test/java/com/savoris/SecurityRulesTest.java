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
    private StaffEntity warehouse;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        owner = createStaff(StaffRole.ADMIN, null);
        manager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        waiter = createStaff(StaffRole.WAITER, branch);
        cashier = createStaff(StaffRole.WAITER, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        warehouse = createStaff(StaffRole.WAREHOUSE, branch);
    }

    private String staffJson(String role, BranchEntity at) {
        int n = unique();
        return "{\"code\":\"N" + n + "\",\"fullName\":\"New " + n + "\",\"username\":\"new" + n + "\","
            + "\"email\":\"new" + n + "@savoris.vn\",\"phone\":\"0900" + n + "\",\"role\":\"" + role + "\","
            + "\"branchId\":" + at.getId() + ",\"hourlyRate\":30000,\"password\":\"Welcome@123\"}";
    }

    @Test
    void waiterCannotCreateAccounts_letAloneAnAdmin() throws Exception {
        mockMvc.perform(post("/staff").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("ADMIN", branch)))
            .andExpect(status().isForbidden());
    }

    @Test
    void managerHiresOnlyFloorAndKitchenStaff_forTheirOwnBranch() throws Exception {
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("WAITER", branch)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("ADMIN", branch)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("BRANCH_MANAGER", branch)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/staff").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("WAITER", createBranch(null))))
            .andExpect(status().isForbidden());

        // The chain owner may create anyone
        mockMvc.perform(post("/staff").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(staffJson("BRANCH_MANAGER", branch)))
            .andExpect(status().isCreated());
    }

    @Test
    void restaurantRouterMatchesThePermissionMatrix() throws Exception {
        MenuItemEntity dish = createMenuItem("Pho " + unique(), 50000);

        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(chef)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(warehouse)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/orders").with(as(chef)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(dish, 1))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(dish, 1))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/inventory/branch/" + branch.getId()).with(as(cashier)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/inventory/branch/" + branch.getId()).with(as(manager)))
            .andExpect(status().isOk());

        mockMvc.perform(put("/kitchen/items/1/start").with(as(manager)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/kitchen/items/1/start").with(as(cashier)))
            .andExpect(status().isForbidden());
    }

    @Test
    void warehouseCanManageStockOnlyAtTheirOwnBranch() throws Exception {
        BranchEntity otherBranch = createBranch(null);

        mockMvc.perform(get("/inventory/branch/" + branch.getId()).with(as(warehouse)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/inventory/branch/" + otherBranch.getId()).with(as(warehouse)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/inventory/branch/" + branch.getId() + "/import").with(as(warehouse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rows\":[{\"name\":\"Rice\",\"unit\":\"kg\",\"quantity\":10,\"minStock\":2}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(1));
        mockMvc.perform(post("/inventory/branch/" + otherBranch.getId() + "/import").with(as(warehouse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rows\":[{\"name\":\"Beef\",\"unit\":\"kg\",\"quantity\":10}]}"))
            .andExpect(status().isForbidden());

        // ADMIN retains the existing all-branch stock access.
        mockMvc.perform(get("/inventory/branch/" + otherBranch.getId()).with(as(owner)))
            .andExpect(status().isOk());
    }

    @Test
    void staffCreationIsWrittenToTheAuditLog() throws Exception {
        String body = staffJson("WAITER", branch);
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
        assertTrue(logs.get(0).getNewValue().path("role").asText().equals("WAITER"));
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
        StaffEntity cashier = createStaff(StaffRole.WAITER, branch);
        String promote = "{\"fullName\":\"X\",\"email\":\"x" + unique() + "@savoris.vn\",\"phone\":\"0900000000\","
            + "\"role\":\"BRANCH_MANAGER\",\"branchId\":" + branch.getId() + ",\"hourlyRate\":1}";
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
    void managerEditsOnlyTheirOwnBranch() throws Exception {
        String update = "{\"name\":\"Renamed\",\"address\":\"1 Street\",\"city\":\"Ha Noi\",\"kitchenCapacity\":30}";
        mockMvc.perform(put("/branches/" + branch.getId()).with(as(manager)).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kitchenCapacity").value(30));
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
        StaffEntity cashier = createStaff(StaffRole.WAITER, branch);
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
    void wrongGuessesAreForgottenAfterASuccessfulLogin() throws Exception {
        StaffEntity cashier = createStaff(StaffRole.WAITER, branch);
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
