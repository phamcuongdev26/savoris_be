package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadyMadeAndStockTest extends IntegrationTestSupport {

    private BranchEntity branch;
    private StaffEntity admin;
    private StaffEntity cashier;
    private StaffEntity waiter;
    private StaffEntity chef;
    private StaffEntity manager;
    private MenuItemEntity coke;

    @BeforeEach
    void setUp() throws Exception {
        branch = createBranch(null);
        admin = createStaff(StaffRole.ADMIN, null);
        cashier = createStaff(StaffRole.WAITER, branch);
        waiter = createStaff(StaffRole.WAITER, branch);
        chef = createStaff(StaffRole.CHEF, branch);
        manager = createStaff(StaffRole.BRANCH_MANAGER, branch);
        coke = createMenuItem("Coca chai " + unique(), 20000);
        mockMvc.perform(put("/menu-items/" + coke.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + coke.getName() + "\",\"needsKitchen\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.needsKitchen").value(false));
    }

    @Test
    void readyMadeDishSkipsTheKitchenAndIsHandedOutByTheFloor() throws Exception {
        String order = mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(branch, "DINE_IN", table(branch, 1), dish(coke, 2))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items[0].status").value("READY"))
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/queue").with(as(chef))).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/kitchen/branches/" + branch.getId() + "/load").with(as(chef))).andExpect(jsonPath("$.loadPercent").value(0));
        int itemId = JsonPath.read(order, "$.items[0].id");
        mockMvc.perform(put("/kitchen/items/" + itemId + "/served").with(as(chef))).andExpect(status().isOk());
    }

    @Test
    void stockImportedByHandOrFileDecidesWhenAReadyMadeDishRunsOut() throws Exception {
        String name = "Coca-Cola chai " + unique();
        String imported = importStock(manager, "{\"name\":\"" + name + "\",\"unit\":\"chai\",\"quantity\":2,\"minStock\":5}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(1))
            .andReturn().getResponse().getContentAsString();
        int ingredientId = JsonPath.read(mockMvc.perform(get("/inventory/branch/" + branch.getId()).with(as(manager)))
            .andReturn().getResponse().getContentAsString(), "$[0].ingredientId");
        org.assertj.core.api.Assertions.assertThat(imported).contains("\"updated\":1");
        mockMvc.perform(put("/menu-items/" + coke.getId() + "/recipe").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"ingredientId\":" + ingredientId + ",\"quantity\":1}]}"))
            .andExpect(status().isOk());

        // Two bottles on the shelf: two can be sold, then the dish is sold out by itself
        sell(2).andExpect(status().isCreated());
        mockMvc.perform(get("/branches/" + branch.getId() + "/menu").with(as(cashier)))
            .andExpect(jsonPath("$[?(@.menuItemId == " + coke.getId() + ")].available").value(false))
            .andExpect(jsonPath("$[?(@.menuItemId == " + coke.getId() + ")].outOfStock").value(true));
        sell(1).andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("MENU_ITEM_UNAVAILABLE"));

        // The next delivery counted in (same name, any case): no duplicate ingredient, the dish is back
        importStock(manager, "{\"name\":\"  " + name.toUpperCase() + " \",\"quantity\":10}")
            .andExpect(jsonPath("$.created").value(0))
            .andExpect(jsonPath("$.updated").value(1));
        mockMvc.perform(get("/branches/" + branch.getId() + "/menu").with(as(cashier)))
            .andExpect(jsonPath("$[?(@.menuItemId == " + coke.getId() + ")].available").value(true));

        // A new ingredient needs its unit; staff of the floor cannot import stock
        importStock(manager, "{\"name\":\"Unknown " + unique() + "\",\"quantity\":1}").andExpect(status().isBadRequest());
        importStock(cashier, "{\"name\":\"" + name + "\",\"quantity\":1}").andExpect(status().isForbidden());
    }

    private ResultActions sell(int quantity) throws Exception {
        return mockMvc.perform(post("/orders").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
            .content(orderJson(branch, "TAKEAWAY", null, dish(coke, quantity))));
    }

    private ResultActions importStock(StaffEntity who, String row) throws Exception {
        return mockMvc.perform(post("/inventory/branch/" + branch.getId() + "/import").with(as(who)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rows\":[" + row + "]}"));
    }
}
