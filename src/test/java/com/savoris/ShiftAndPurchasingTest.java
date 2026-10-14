package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StockMovementType;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.BranchInventoryEntity;
import com.savoris.entity.IngredientEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.entity.StaffShiftEntity;
import com.savoris.entity.SupplierEntity;
import com.savoris.repository.BranchInventoryRepository;
import com.savoris.repository.IngredientRepository;
import com.savoris.repository.StaffShiftRepository;
import com.savoris.repository.StockMovementRepository;
import com.savoris.repository.SupplierRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShiftAndPurchasingTest extends IntegrationTestSupport {

    @Autowired StaffShiftRepository staffShiftRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired BranchInventoryRepository branchInventoryRepository;
    @Autowired StockMovementRepository stockMovementRepository;

    private BranchEntity branch;
    private StaffEntity manager;
    private StaffEntity waiter;
    private StaffEntity cashier;
    private StaffEntity warehouse;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        manager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        cashier = createStaff(StaffRole.WAITER, branch);
        cashier.setHourlyRate(BigDecimal.valueOf(30000));
        staffRepository.save(cashier);
        waiter = createStaff(StaffRole.WAITER, branch);
        warehouse = createStaff(StaffRole.WAREHOUSE, branch);
    }

    // ---------------------------------------------------------------- shifts

    private int createShift(LocalTime start, LocalTime end) throws Exception {
        String json = mockMvc.perform(post("/branches/" + branch.getId() + "/shifts").with(as(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ca " + unique() + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String assignJson(StaffEntity staff, LocalDate date) {
        return "{\"staffId\":" + staff.getId() + ",\"workDate\":\"" + date + "\"}";
    }

    @Test
    void managerSchedulesStaff_andTheRulesHold() throws Exception {
        int shiftId = createShift(LocalTime.of(7, 0), LocalTime.of(15, 0));
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(assignJson(cashier, tomorrow)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.staffName").value(cashier.getFullName()))
            .andExpect(jsonPath("$.startTime").value("07:00:00"));

        // Same person, same shift, same day
        mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(assignJson(cashier, tomorrow)))
            .andExpect(status().isConflict());
        // Staff of another branch
        StaffEntity outsider = createStaff(StaffRole.WAITER, createBranch(null));
        mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(assignJson(outsider, tomorrow)))
            .andExpect(status().isBadRequest());
        // A day in the past
        mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(assignJson(cashier, LocalDate.now().minusDays(1))))
            .andExpect(status().isBadRequest());
        // Waiters do not make the schedule
        mockMvc.perform(post("/branches/" + branch.getId() + "/shifts").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"startTime\":\"07:00\",\"endTime\":\"15:00\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/branches/" + branch.getId() + "/schedule").with(as(cashier))
                .param("from", tomorrow.toString()).param("to", tomorrow.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/shifts/me").with(as(cashier))
                .param("from", LocalDate.now().toString()).param("to", tomorrow.plusDays(6).toString()))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void staffCheckInAndOutOfTheirOwnShift_andTheTimesheetPaysTheHoursWorked() throws Exception {
        // A shift that is running right now
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = now.minusMinutes(10);
        int shiftId = createShift(start.toLocalTime(), start.toLocalTime().plusHours(4));
        String assigned = mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager))
                .contentType(MediaType.APPLICATION_JSON).content(assignJson(cashier, start.toLocalDate())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int assignmentId = JsonPath.read(assigned, "$.id");

        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-in").with(as(manager)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-out").with(as(cashier)))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-in").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkInAt").exists());
        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-in").with(as(cashier)))
            .andExpect(status().isConflict());
        mockMvc.perform(delete("/shift-assignments/" + assignmentId).with(as(manager)))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-out").with(as(cashier)))
            .andExpect(status().isOk());

        // Pretend the shift lasted exactly 3.5 hours to check the pay maths
        StaffShiftEntity row = staffShiftRepository.findById(assignmentId).orElseThrow();
        row.setCheckInAt(start);
        row.setCheckOutAt(start.plusMinutes(210));
        staffShiftRepository.save(row);

        mockMvc.perform(get("/branches/" + branch.getId() + "/timesheet").with(as(manager))
                .param("from", start.toLocalDate().toString()).param("to", start.toLocalDate().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].staffId").value(cashier.getId()))
            .andExpect(jsonPath("$[0].shiftsWorked").value(1))
            .andExpect(jsonPath("$[0].minutesWorked").value(210))
            .andExpect(jsonPath("$[0].hoursWorked").value(3.5))
            .andExpect(jsonPath("$[0].wage").value(105000));

        mockMvc.perform(get("/branches/" + branch.getId() + "/timesheet").with(as(cashier))
                .param("from", start.toLocalDate().toString()).param("to", start.toLocalDate().toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void checkInIsRefusedWellBeforeTheShift() throws Exception {
        LocalDateTime later = LocalDateTime.now().plusHours(3);
        int shiftId = createShift(later.toLocalTime(), later.toLocalTime().plusHours(2));
        String assigned = mockMvc.perform(post("/shifts/" + shiftId + "/assignments").with(as(manager))
                .contentType(MediaType.APPLICATION_JSON).content(assignJson(cashier, later.toLocalDate())))
            .andReturn().getResponse().getContentAsString();
        int assignmentId = JsonPath.read(assigned, "$.id");
        mockMvc.perform(put("/shift-assignments/" + assignmentId + "/check-in").with(as(cashier)))
            .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- purchasing

    @Test
    void purchaseOrderGoesDraftOrderedReceived_andTheStockGoesUp() throws Exception {
        SupplierEntity supplier = supplier();
        IngredientEntity beef = ingredient("Beef");
        IngredientEntity rice = ingredient("Rice");
        stock(beef, "2");  // rice is not stocked here yet: receiving creates its row

        String draft = mockMvc.perform(post("/purchase-orders").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"supplierId\":" + supplier.getId() + ",\"items\":["
                    + "{\"ingredientId\":" + beef.getId() + ",\"quantity\":10,\"unitPrice\":250000},"
                    + "{\"ingredientId\":" + rice.getId() + ",\"quantity\":50,\"unitPrice\":18000}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("PN" + branch.getId() + "-")))
            .andExpect(jsonPath("$.totalAmount").value(3400000))
            .andReturn().getResponse().getContentAsString();
        int poId = JsonPath.read(draft, "$.id");
        int beefLine = JsonPath.read(draft, "$.items[0].id");

        mockMvc.perform(put("/purchase-orders/" + poId + "/receive").with(as(manager))).andExpect(status().isConflict());
        mockMvc.perform(put("/purchase-orders/" + poId + "/order").with(as(warehouse))).andExpect(status().isForbidden());
        mockMvc.perform(put("/purchase-orders/" + poId + "/order").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ORDERED"));

        // Only 8 kg of beef arrived; rice in full
        mockMvc.perform(put("/purchase-orders/" + poId + "/receive").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"itemId\":" + beefLine + ",\"receivedQuantity\":8}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andExpect(jsonPath("$.totalAmount").value(2900000));

        assertThat(stockOf(beef)).isEqualByComparingTo("10");
        assertThat(stockOf(rice)).isEqualByComparingTo("50");
        assertThat(stockMovementRepository.findAll().stream()
            .filter(m -> poId == (m.getPurchaseOrderId() == null ? -1 : m.getPurchaseOrderId()))
            .allMatch(m -> m.getType() == StockMovementType.IMPORT)).isTrue();

        // Received goods cannot be received again or cancelled
        mockMvc.perform(put("/purchase-orders/" + poId + "/receive").with(as(manager))).andExpect(status().isConflict());
        mockMvc.perform(put("/purchase-orders/" + poId + "/cancel").with(as(manager))).andExpect(status().isConflict());
        assertThat(stockOf(beef)).isEqualByComparingTo("10");
    }

    @Test
    void purchaseOrderRulesAreEnforced() throws Exception {
        SupplierEntity supplier = supplier();
        IngredientEntity beef = ingredient("Beef");
        String line = "{\"ingredientId\":" + beef.getId() + ",\"quantity\":1,\"unitPrice\":1000}";

        mockMvc.perform(post("/purchase-orders").with(as(waiter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"supplierId\":" + supplier.getId() + ",\"items\":[" + line + "]}"))
            .andExpect(status().isForbidden());
        StaffEntity otherWarehouse = createStaff(StaffRole.WAREHOUSE, createBranch(null));
        mockMvc.perform(post("/purchase-orders").with(as(otherWarehouse)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"supplierId\":" + supplier.getId() + ",\"items\":[" + line + "]}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/purchase-orders").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"supplierId\":" + supplier.getId() + ",\"items\":[" + line + "," + line + "]}"))
            .andExpect(status().isBadRequest());

        String draft = mockMvc.perform(post("/purchase-orders").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"supplierId\":" + supplier.getId() + ",\"items\":[" + line + "]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int poId = JsonPath.read(draft, "$.id");
        mockMvc.perform(put("/purchase-orders/" + poId + "/cancel").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/purchase-orders/branch/" + branch.getId()).with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/ingredients").with(as(manager))).andExpect(status().isOk());
    }

    private SupplierEntity supplier() {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setName("Supplier " + unique());
        supplier.setIsActive(true);
        return supplierRepository.save(supplier);
    }

    private IngredientEntity ingredient(String name) {
        IngredientEntity ingredient = new IngredientEntity();
        ingredient.setCode("P" + unique());
        ingredient.setName(name);
        ingredient.setUnit("kg");
        return ingredientRepository.save(ingredient);
    }

    private void stock(IngredientEntity ingredient, String quantity) {
        BranchInventoryEntity stock = new BranchInventoryEntity();
        stock.setBranchId(branch.getId());
        stock.setIngredientId(ingredient.getId());
        stock.setQuantity(new BigDecimal(quantity));
        branchInventoryRepository.save(stock);
    }

    private BigDecimal stockOf(IngredientEntity ingredient) {
        return branchInventoryRepository.findByBranchIdAndIngredientId(branch.getId(), ingredient.getId()).orElseThrow().getQuantity();
    }
}
