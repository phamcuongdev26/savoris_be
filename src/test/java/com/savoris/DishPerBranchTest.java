package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishPerBranchTest extends IntegrationTestSupport {

    private BranchEntity sells;
    private BranchEntity doesNot;
    private StaffEntity admin;
    private StaffEntity cashierThere;
    private MenuItemEntity crab;

    @BeforeEach
    void setUp() {
        sells = createBranch(null);
        doesNot = createBranch(null);
        admin = createStaff(StaffRole.ADMIN, null);
        cashierThere = createStaff(StaffRole.STAFF, doesNot);
        crab = createMenuItem("Banh canh cua", 60000);
    }

    @Test
    void ownerChoosesWhichBranchesSellADish() throws Exception {
        // A new dish is sold everywhere
        mockMvc.perform(get("/menu-items/" + crab.getId() + "/branches").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.branchId == " + doesNot.getId() + ")].offered").value(true));

        mockMvc.perform(put("/menu-items/" + crab.getId() + "/branches").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[" + sells.getId() + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.branchId == " + sells.getId() + ")].offered").value(true))
            .andExpect(jsonPath("$[?(@.branchId == " + doesNot.getId() + ")].offered").value(false));

        // Gone from that branch's menus (staff, table QR, takeaway), and an order for it is refused
        mockMvc.perform(get("/branches/" + doesNot.getId() + "/menu").with(as(cashierThere)))
            .andExpect(jsonPath("$[*].menuItemId", not(hasItem(crab.getId()))));
        mockMvc.perform(get("/public/branches/" + doesNot.getId() + "/menu"))
            .andExpect(jsonPath("$.menu[*].menuItemId", not(hasItem(crab.getId()))));
        mockMvc.perform(get("/public/branches/" + sells.getId() + "/menu"))
            .andExpect(jsonPath("$.menu[*].menuItemId", hasItem(crab.getId())));
        mockMvc.perform(post("/orders").with(as(cashierThere)).contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(doesNot, "TAKEAWAY", null, dish(crab, 1))))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.code").value("MENU_ITEM_UNAVAILABLE"));
        mockMvc.perform(get("/menu-items/branch-exclusions").with(as(admin)))
            .andExpect(jsonPath("$[?(@.menuItemId == " + crab.getId() + " && @.branchId == " + doesNot.getId() + ")]").exists());

        // Giving it back puts it on the menu again; only the owner decides
        mockMvc.perform(put("/menu-items/" + crab.getId() + "/branches").with(as(cashierThere)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[]}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/menu-items/" + crab.getId() + "/branches").with(as(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchIds\":[" + sells.getId() + "," + doesNot.getId() + "]}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/branches/" + doesNot.getId() + "/menu").with(as(cashierThere)))
            .andExpect(jsonPath("$[*].menuItemId", hasItem(crab.getId())));
    }
}
