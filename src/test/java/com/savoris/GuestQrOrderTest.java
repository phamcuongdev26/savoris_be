package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.constant.TableStatus;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestQrOrderTest extends IntegrationTestSupport {

    private BranchEntity branch;
    private StaffEntity waiter;
    private MenuItemEntity pho;
    private DiningTableEntity table;

    @BeforeEach
    void setUp() {
        branch = createBranch(null);
        waiter = createStaff(StaffRole.WAITER, branch);
        pho = createMenuItem("Pho bo", 50000);
        table = diningTableRepository.findById(table(branch, 3)).orElseThrow();
    }

    @Test
    void everyTableGetsItsOwnQrToken() throws Exception {
        assertThat(table.getQrToken()).hasSize(32);
        mockMvc.perform(get("/branches/" + branch.getId() + "/tables").with(as(waiter)))
            .andExpect(jsonPath("$[2].qrToken").value(table.getQrToken()));
    }

    @Test
    void guestSeesBranchTableAndMenuWithoutLoggingIn() throws Exception {
        overrideAtBranch(branch, pho, 55000L, true);
        mockMvc.perform(get("/public/tables/" + table.getQrToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.branchName").value(branch.getName()))
            .andExpect(jsonPath("$.tableName").value("T3"))
            .andExpect(jsonPath("$.capacity").value(table.getCapacity()))
            .andExpect(jsonPath("$.areaName").value("Floor 1"))
            .andExpect(jsonPath("$.menu[?(@.menuItemId == " + pho.getId() + ")].price").value(55000));

        mockMvc.perform(get("/public/tables/" + table.getQrToken() + "/order")).andExpect(status().isNoContent());
        mockMvc.perform(get("/public/tables/not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void homePageShowsTheChainMenuWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/public/menu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dishes[?(@.id == " + pho.getId() + ")].name").value("Pho bo"))
            .andExpect(jsonPath("$.dishes[?(@.id == " + pho.getId() + ")].price").value(50000))
            .andExpect(jsonPath("$.vatPercent").isNumber());
    }

    @Test
    void guestOrdersGoToTheTablesOpenBillAndTheKitchen() throws Exception {
        pho.setImageUrl("https://example.com/pho.jpg");
        menuItemRepository.save(pho);
        mockMvc.perform(get("/public/tables/" + table.getQrToken()))
            .andExpect(jsonPath("$.menu[?(@.menuItemId == " + pho.getId() + ")].imageUrl").value("https://example.com/pho.jpg"));
        String first = guestOrder(pho, 2);
        assertThat((Integer) JsonPath.read(first, "$.items.length()")).isEqualTo(1);
        assertThat(diningTableRepository.findById(table.getId()).orElseThrow().getStatus()).isEqualTo(TableStatus.OCCUPIED);

        // A second round from another phone at the same table lands on the same bill
        String second = guestOrder(pho, 1);
        assertThat((String) JsonPath.read(second, "$.code")).isEqualTo(JsonPath.read(first, "$.code"));
        assertThat(((Number) JsonPath.read(second, "$.subtotal")).longValue()).isEqualTo(150000);

        // Staff see it like any other order, and the kitchen queue has the dishes
        mockMvc.perform(get("/orders/branch/" + branch.getId()).with(as(waiter)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].tableId").value(table.getId()))
            // flagged so the floor staff's screens ring for dishes nobody on the team entered
            .andExpect(jsonPath("$[0].items[0].orderedByGuest").value(true));
        mockMvc.perform(get("/public/tables/" + table.getQrToken() + "/order"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].imageUrl").value("https://example.com/pho.jpg"))
            .andExpect(jsonPath("$.staffId").doesNotExist());
    }

    @Test
    void soldOutDishIsRefused() throws Exception {
        overrideAtBranch(branch, pho, null, false);
        mockMvc.perform(post("/public/tables/" + table.getQrToken() + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"menuItemId\":" + pho.getId() + ",\"quantity\":1}]}"))
            .andExpect(status().is4xxClientError());
    }

    private String guestOrder(MenuItemEntity item, int quantity) throws Exception {
        return mockMvc.perform(post("/public/tables/" + table.getQrToken() + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"menuItemId\":" + item.getId() + ",\"quantity\":" + quantity + "}]}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }
}
