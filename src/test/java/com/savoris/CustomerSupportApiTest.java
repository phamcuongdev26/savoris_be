package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.StaffEntity;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerSupportApiTest extends IntegrationTestSupport {
    private BranchEntity firstBranch;
    private StaffEntity firstStaff;
    private StaffEntity otherStaff;
    private StaffEntity admin;

    @BeforeEach
    void setup() {
        firstBranch = createBranch(null);
        BranchEntity secondBranch = createBranch(null);
        firstStaff = createStaff(StaffRole.STAFF, firstBranch);
        otherStaff = createStaff(StaffRole.MANAGER, secondBranch);
        admin = createStaff(StaffRole.ADMIN, null);
    }

    @Test
    void publicCustomerCanCreateTicketAndOnlyAssignedBranchStaffCanHandleIt() throws Exception {
        String response = mockMvc.perform(post("/public/support-tickets").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerName":"Nguyễn An","customerPhone":"0912345678","branchId":%d,
                     "issue":"Món ăn có đậu phộng không?","conversationContext":"Trợ lý chưa chắc chắn"}
                    """.formatted(firstBranch.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.referenceCode").isNotEmpty())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(response, "$.referenceCode");
        String accessToken = JsonPath.read(response, "$.accessToken");
        String headers = "X-Support-Token";
        String messagesUrl = "/public/support-tickets/" + reference + "/messages";

        String firstStaffQueue = mockMvc.perform(get("/support-tickets").with(as(firstStaff)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int ticketId = JsonPath.read(firstStaffQueue, "$[0].id");

        mockMvc.perform(get("/support-tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/support-tickets").with(as(firstStaff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].customerName").value("Nguyễn An"));
        mockMvc.perform(get("/support-tickets").with(as(otherStaff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/support-tickets").with(as(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ticketId));

        mockMvc.perform(get(messagesUrl).header(headers, "wrong-token"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(messagesUrl).header(headers, accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderType").value("CUSTOMER"))
            .andExpect(jsonPath("$[0].body").value("Món ăn có đậu phộng không?"))
            .andExpect(jsonPath("$[1].senderType").value("ASSISTANT"));
        mockMvc.perform(post(messagesUrl).header(headers, accessToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Mình bị dị ứng đậu phộng.\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].body").value("Mình bị dị ứng đậu phộng."));
        mockMvc.perform(get("/support-tickets/{id}/messages", ticketId).with(as(otherStaff)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/support-tickets/{id}/messages", ticketId).with(as(firstStaff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].senderType").value("CUSTOMER"));
        mockMvc.perform(post("/support-tickets/{id}/messages", ticketId).with(as(firstStaff)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Nhân viên sẽ kiểm tra thành phần và gọi lại bạn.\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[3].senderType").value("STAFF"));
        mockMvc.perform(get(messagesUrl).header(headers, accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[3].body").value("Nhân viên sẽ kiểm tra thành phần và gọi lại bạn."));

        mockMvc.perform(patch("/support-tickets/{id}", ticketId).with(as(otherStaff)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/support-tickets/{id}", ticketId).with(as(firstStaff)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void publicTicketValidatesCustomerContactAndStatus() throws Exception {
        mockMvc.perform(post("/public/support-tickets").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerName":"A","customerPhone":"12","issue":"Help"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
