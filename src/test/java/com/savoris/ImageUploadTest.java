package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.StaffEntity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageUploadTest extends IntegrationTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Test
    void photoUploadSaysWhenCloudinaryIsNotSetUp() throws Exception {
        StaffEntity admin = createStaff(StaffRole.ADMIN, null);
        mockMvc.perform(multipart("/images").file(new MockMultipartFile("file", "pho.png", "image/png", PNG)).with(as(admin)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("IMAGE_STORAGE_NOT_CONFIGURED"));
        mockMvc.perform(multipart("/images").file(new MockMultipartFile("file", "pho.png", "image/png", "text".getBytes())).with(as(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerUploadsPhotos() throws Exception {
        StaffEntity cashier = createStaff(StaffRole.STAFF, createBranch(null));
        mockMvc.perform(multipart("/images").file(new MockMultipartFile("file", "pho.png", "image/png", PNG)).with(as(cashier)))
            .andExpect(status().isForbidden());
    }

    @Test
    void managerCanUploadSiteAndLocalImages_butNotStaffOrChef() throws Exception {
        BranchEntity branch = createBranch(null);
        StaffEntity manager = createStaff(StaffRole.MANAGER, branch);
        StaffEntity staff = createStaff(StaffRole.STAFF, branch);
        StaffEntity chef = createStaff(StaffRole.CHEF, branch);
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", PNG);

        // /images/site: MANAGER allowed — 503 (Cloudinary not configured), not 403
        mockMvc.perform(multipart("/images/site").file(file).with(as(manager)))
            .andExpect(status().isServiceUnavailable());
        mockMvc.perform(multipart("/images/site").file(file).with(as(staff)))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/images/site").file(file).with(as(chef)))
            .andExpect(status().isForbidden());

        // /admin/media: MANAGER allowed — 400 (fake PNG passes magic check but fails ImageIO.read), not 403
        mockMvc.perform(multipart("/admin/media").file(file).param("alt", "gallery").with(as(manager)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/admin/media").file(file).param("alt", "gallery").with(as(staff)))
            .andExpect(status().isForbidden());
    }
}
