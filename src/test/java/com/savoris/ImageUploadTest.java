package com.savoris;

import com.savoris.constant.StaffRole;
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
        StaffEntity cashier = createStaff(StaffRole.CASHIER, createBranch(null));
        mockMvc.perform(multipart("/images").file(new MockMultipartFile("file", "pho.png", "image/png", PNG)).with(as(cashier)))
            .andExpect(status().isForbidden());
    }
}
