package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.StaffEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class KitchenEventStreamTest extends IntegrationTestSupport {

    @Test
    void kitchenStreamRequiresAuthentication() throws Exception {
        BranchEntity branch = createBranch(20);

        mockMvc.perform(get("/kitchen/branches/{branchId}/events", branch.getId()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void kitchenStreamRequiresKitchenRole() throws Exception {
        BranchEntity branch = createBranch(20);
        StaffEntity staff = createStaff(StaffRole.STAFF, branch);

        mockMvc.perform(get("/kitchen/branches/{branchId}/events", branch.getId()).with(as(staff)))
            .andExpect(status().isForbidden());
    }

    @Test
    void kitchenStreamIsScopedToStaffBranch() throws Exception {
        BranchEntity branch = createBranch(20);
        BranchEntity otherBranch = createBranch(20);
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);

        mockMvc.perform(get("/kitchen/branches/{branchId}/events", otherBranch.getId()).with(as(chef)))
            .andExpect(status().isForbidden());
    }

    @Test
    void chefCanOpenKitchenEventStream() throws Exception {
        BranchEntity branch = createBranch(20);
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);

        MvcResult result = mockMvc.perform(get("/kitchen/branches/{branchId}/events", branch.getId())
                .with(as(chef)).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

        org.junit.jupiter.api.Assertions.assertTrue(
            result.getResponse().getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }
}
