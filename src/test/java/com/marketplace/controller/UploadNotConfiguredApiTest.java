package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Chưa cấu hình Cloudinary: API ký trả 503, nhưng vẫn kiểm tra quyền trước. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadNotConfiguredApiTest {

  private static final String SIGNATURE = "/api/v1/uploads/signature";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;

  @Test
  void anAllowedCallerGets503() throws Exception {
    support.send(mvc, post(SIGNATURE), support.user(Role.SELLER, SellerStatus.APPROVED), "{\"purpose\":\"PRODUCT\"}")
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("UPLOAD_NOT_CONFIGURED"));
  }

  @Test
  void aCallerWithoutPermissionStillGets403() throws Exception {
    support.send(mvc, post(SIGNATURE), support.user(Role.CUSTOMER, null), "{\"purpose\":\"PRODUCT\"}")
        .andExpect(status().isForbidden());
  }
}
