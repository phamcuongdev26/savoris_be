package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StockMovementType;
import com.savoris.constant.TableStatus;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.BranchInventoryEntity;
import com.savoris.entity.CustomerEntity;
import com.savoris.entity.IngredientEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.RecipeEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.BranchInventoryRepository;
import com.savoris.repository.CustomerRepository;
import com.savoris.repository.IngredientRepository;
import com.savoris.repository.RecipeRepository;
import com.savoris.repository.StockMovementRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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

class TableStockReservationTest extends IntegrationTestSupport {

    @Autowired IngredientRepository ingredientRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired BranchInventoryRepository branchInventoryRepository;
    @Autowired StockMovementRepository stockMovementRepository;
    @Autowired CustomerRepository customerRepository;

    private BranchEntity branch;
    private StaffEntity cashier;
    private StaffEntity chef;
    private StaffEntity manager;
    private MenuItemEntity pho;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        cashier = createStaff(StaffRole.STAFF, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        manager = createStaff(StaffRole.MANAGER, branch);
        pho = createMenuItem("Pho bo", 50000);
    }

    // ---------- tables ----------

    @Test
    void tableIsOccupiedWhileGuestsHaveAnOpenOrder_andFreeAfterTheLastOneCloses() throws Exception {
        int tableId = table(branch, 2);
        String first = createOrder("DINE_IN", tableId);
        String second = createOrder("DINE_IN", tableId);  // guests order another round
        assertThat(tableStatus(tableId)).isEqualTo(TableStatus.OCCUPIED);

        mockMvc.perform(get("/branches/" + branch.getId() + "/tables").with(as(cashier)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[?(@.id == " + tableId + ")].status").value("OCCUPIED"));

        mockMvc.perform(put("/orders/" + JsonPath.read(first, "$.id") + "/cancel").with(as(cashier))).andExpect(status().isOk());
        assertThat(tableStatus(tableId)).as("still one open order").isEqualTo(TableStatus.OCCUPIED);

        mockMvc.perform(put("/orders/" + JsonPath.read(second, "$.id") + "/cancel").with(as(cashier))).andExpect(status().isOk());
        assertThat(tableStatus(tableId)).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void tableNamesAreUniqueWithinABranch() throws Exception {
        StaffEntity manager = createStaff(StaffRole.MANAGER, branch);
        String area = mockMvc.perform(post("/branches/" + branch.getId() + "/areas").with(as(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Floor 2\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int areaId = JsonPath.read(area, "$.id");

        // T3 already exists on Floor 1: another area of the same branch cannot reuse it, whatever the case
        mockMvc.perform(post("/areas/" + areaId + "/tables").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" t3 \",\"capacity\":4}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_TABLE_NAME"));
        mockMvc.perform(put("/tables/" + table(branch, 2)).with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T3\",\"capacity\":4}"))
            .andExpect(status().isConflict());

        // Keeping its own name while changing seats is fine, and another branch has its own T3
        mockMvc.perform(put("/tables/" + table(branch, 2)).with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T2\",\"capacity\":6}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(6));
        BranchEntity other = createBranch(null);
        StaffEntity otherManager = createStaff(StaffRole.MANAGER, other);
        int otherArea = areaRepository.findByBranchIdOrderByName(other.getId()).get(0).getId();
        mockMvc.perform(post("/areas/" + otherArea + "/tables").with(as(otherManager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T9\",\"capacity\":2}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/areas/" + areaId + "/tables").with(as(manager)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T9\",\"capacity\":2}"))
            .andExpect(status().isCreated());
    }

    @Test
    void tableOfAnotherBranchIsRejected() throws Exception {
        BranchEntity other = createBranch(null);
        mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(other, 1), dish(pho, 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- stock ----------

    @Test
    void startingToCookDeductsIngredientsByRecipe_andRecordsTheMovement() throws Exception {
        IngredientEntity beef = ingredient("Beef", "2");
        recipe(pho, beef, "0.15");
        stock(beef, "10");

        String created = createOrder("TAKEAWAY", null, 3);
        int itemId = JsonPath.read(created, "$.items[0].id");
        int orderId = JsonPath.read(created, "$.id");
        mockMvc.perform(put("/kitchen/items/" + itemId + "/start").with(as(chef))).andExpect(status().isOk());

        // 10 - 3 x 0.15 = 9.55
        assertThat(stockOf(beef)).isEqualByComparingTo("9.55");
        var movements = stockMovementRepository.findByOrderId(orderId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo(StockMovementType.EXPORT_KITCHEN);
        assertThat(movements.get(0).getQuantity()).isEqualByComparingTo("-0.45");
        assertThat(movements.get(0).getCreatedBy()).isEqualTo(chef.getId());
    }

    @Test
    void lowStockListsOnlyIngredientsAtOrBelowTheirMinimum() throws Exception {
        IngredientEntity beef = ingredient("Beef", "2");
        IngredientEntity rice = ingredient("Rice", "5");
        stock(beef, "1.5");
        stock(rice, "40");
        mockMvc.perform(get("/inventory/branch/" + branch.getId() + "/low-stock").with(as(manager)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].ingredientId").value(beef.getId()));

        // Waiters have no business in the store room
        StaffEntity waiter = createStaff(StaffRole.STAFF, branch);
        mockMvc.perform(get("/inventory/branch/" + branch.getId() + "/low-stock").with(as(waiter)))
            .andExpect(status().isForbidden());
    }

    @Test
    void stockCorrectionIsKeptInTheHistory() throws Exception {
        IngredientEntity beef = ingredient("Beef", "2");
        stock(beef, "10");
        mockMvc.perform(put("/inventory/branch/" + branch.getId() + "/ingredient/" + beef.getId()).with(as(manager))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":8,\"notes\":\"stock count\"}"))
            .andExpect(status().isOk());

        var adjustment = stockMovementRepository.findAll().stream()
            .filter(m -> m.getIngredientId().equals(beef.getId()) && m.getType() == StockMovementType.ADJUSTMENT)
            .toList();
        assertThat(adjustment).hasSize(1);
        assertThat(adjustment.get(0).getQuantity()).isEqualByComparingTo("-2");
    }

    // ---------- reservations ----------

    @Test
    void reservationIsSavedWithGuestDetails_andFollowsItsLifecycle() throws Exception {
        CustomerEntity customer = new CustomerEntity();
        customer.setPhone("0912" + unique());
        customer.setFullName("Nguyen Van A");
        customer = customerRepository.save(customer);
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withNano(0);

        String created = mockMvc.perform(post("/reservations").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"customerId\":" + customer.getId()
                    + ",\"reservationDate\":\"" + tomorrow + "\",\"numberOfGuests\":6,\"notes\":\"birthday\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.numberOfGuests").value(6))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.notes").value("birthday"))
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/reservations/" + id + "/confirm").with(as(cashier)))
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(put("/reservations/" + id + "/confirm").with(as(cashier)))
            .andExpect(status().isConflict());
        mockMvc.perform(put("/reservations/" + id + "/cancel").with(as(cashier)))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        StaffEntity outsider = createStaff(StaffRole.STAFF, createBranch(null));
        mockMvc.perform(get("/reservations/" + id).with(as(outsider))).andExpect(status().isForbidden());
    }

    @Test
    void reservationInThePastIsRejected() throws Exception {
        CustomerEntity customer = new CustomerEntity();
        customer.setPhone("0913" + unique());
        customer = customerRepository.save(customer);

        mockMvc.perform(post("/reservations").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"customerId\":" + customer.getId()
                    + ",\"reservationDate\":\"2020-01-01T19:00:00\",\"numberOfGuests\":2}"))
            .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private String createOrder(String type, Integer tableId) throws Exception {
        return createOrder(type, tableId, 1);
    }

    private String createOrder(String type, Integer tableId, int quantity) throws Exception {
        return mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, type, tableId, dish(pho, quantity))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private TableStatus tableStatus(int tableId) {
        return diningTableRepository.findById(tableId).orElseThrow().getStatus();
    }

    private IngredientEntity ingredient(String name, String minStock) {
        IngredientEntity ingredient = new IngredientEntity();
        ingredient.setCode("I" + unique());
        ingredient.setName(name);
        ingredient.setUnit("kg");
        ingredient.setMinStock(new BigDecimal(minStock));
        return ingredientRepository.save(ingredient);
    }

    private void recipe(MenuItemEntity dish, IngredientEntity ingredient, String perPortion) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setMenuItemId(dish.getId());
        recipe.setIngredientId(ingredient.getId());
        recipe.setQuantity(new BigDecimal(perPortion));
        recipeRepository.save(recipe);
    }

    private void stock(IngredientEntity ingredient, String quantity) {
        BranchInventoryEntity stock = new BranchInventoryEntity();
        stock.setBranchId(branch.getId());
        stock.setIngredientId(ingredient.getId());
        stock.setQuantity(new BigDecimal(quantity));
        branchInventoryRepository.save(stock);
    }

    private BigDecimal stockOf(IngredientEntity ingredient) {
        List<BranchInventoryEntity> rows = branchInventoryRepository.findByBranchId(branch.getId());
        return rows.stream().filter(r -> r.getIngredientId().equals(ingredient.getId())).findFirst().orElseThrow().getQuantity();
    }
}
