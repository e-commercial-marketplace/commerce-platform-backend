package com.marketplace.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.cloudinary.CloudinaryGateway;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.support.TestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@code POST /uploads/signature} và luật URL ảnh khi đã cấu hình Cloudinary (features/upload/API_CONTRACT.md). */
@SpringBootTest(
    properties = {
      "app.cloudinary.cloud-name=demo",
      "app.cloudinary.api-key=public-key",
      "app.cloudinary.api-secret=test-secret"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadApiTest {

  private static final String SIGNATURE = "/api/v1/uploads/signature";
  private static final String OWN_IMAGE = "https://res.cloudinary.com/demo/image/upload/v1/marketplace/a.jpg";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ObjectMapper json;
  @Autowired private CloudinaryGateway gateway;

  private User seller;
  private User customer;
  private Category category;

  @BeforeEach
  void setUp() {
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    customer = support.user(Role.CUSTOMER, null);
    category = support.category();
  }

  @Test
  void anApprovedSellerGetsASignatureThatMatchesTheReturnedParameters() throws Exception {
    String body =
        support
            .send(mvc, post(SIGNATURE), seller, "{\"purpose\":\"PRODUCT\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadUrl").value("https://api.cloudinary.com/v1_1/demo/image/upload"))
            .andExpect(jsonPath("$.data.apiKey").value("public-key"))
            .andExpect(jsonPath("$.data.publicId").value(matchesPattern("marketplace/products/[0-9a-f-]{36}")))
            .andExpect(jsonPath("$.data.allowedFormats").value("jpg,png,webp"))
            .andExpect(jsonPath("$.data.overwrite").value(false))
            .andExpect(jsonPath("$.data.apiSecret").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode data = json.readTree(body).get("data");
    org.assertj.core.api.Assertions.assertThat(data.get("signature").asText())
        .isEqualTo(gateway.signUpload(data.get("publicId").asText(), data.get("timestamp").asLong()));
  }

  @Test
  void chatImagesAreForChatParticipants() throws Exception {
    support.send(mvc, post(SIGNATURE), customer, "{\"purpose\":\"CHAT\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publicId").value(matchesPattern("marketplace/chat/[0-9a-f-]{36}")));
    support.send(mvc, post(SIGNATURE), seller, "{\"purpose\":\"CHAT\"}").andExpect(status().isOk());
    support.send(mvc, post(SIGNATURE), support.user(Role.ADMIN, null), "{\"purpose\":\"CHAT\"}")
        .andExpect(status().isForbidden());
  }

  @Test
  void productImagesAreOnlyForApprovedSellers() throws Exception {
    support.send(mvc, post(SIGNATURE), customer, "{\"purpose\":\"PRODUCT\"}").andExpect(status().isForbidden());
    support.send(mvc, post(SIGNATURE), support.user(Role.SELLER, SellerStatus.PENDING), "{\"purpose\":\"PRODUCT\"}")
        .andExpect(status().isForbidden());
    // Seller bị tạm ngưng: quyền đọc từ DB nên bị chặn dù token vẫn hợp lệ.
    support.send(mvc, post(SIGNATURE), support.user(Role.SELLER, SellerStatus.SUSPENDED), "{\"purpose\":\"PRODUCT\"}")
        .andExpect(status().isForbidden());
  }

  @Test
  void aGuestOrAnInvalidBodyIsRejected() throws Exception {
    mvc.perform(post(SIGNATURE).contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"CHAT\"}"))
        .andExpect(status().isUnauthorized());
    support.send(mvc, post(SIGNATURE), seller, "{}").andExpect(status().isBadRequest());
    support.send(mvc, post(SIGNATURE), seller, "{\"purpose\":\"AVATAR\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aProductMustUseAnImageOfTheProjectCloud() throws Exception {
    support.send(mvc, post("/api/v1/seller/products"), seller, productJson("https://evil.com/a.jpg"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    support.send(mvc, post("/api/v1/seller/products"), seller, productJson(OWN_IMAGE)).andExpect(status().isCreated());
  }

  @Test
  void anOldProductKeepsWorkingUntilItsImageChanges() throws Exception {
    // support.product lưu thẳng ảnh example.com, giống sản phẩm seed có từ trước khi có luật này.
    Product old = support.product(seller, category, "Old", "10.00", 1);
    String path = "/api/v1/seller/products/" + old.getId();
    support.send(mvc, patch(path), seller, "{\"price\":\"20.00\"}").andExpect(status().isOk());
    support.send(mvc, patch(path), seller, "{\"imageUrl\":\"" + old.getImageUrl() + "\"}").andExpect(status().isOk());
    support.send(mvc, patch(path), seller, "{\"imageUrl\":\"https://evil.com/a.jpg\"}").andExpect(status().isBadRequest());
    support.send(mvc, patch(path), seller, "{\"imageUrl\":\"" + OWN_IMAGE + "\"}").andExpect(status().isOk());
  }

  @Test
  void aChatMessageMustUseAnImageOfTheProjectCloud() throws Exception {
    String opened =
        support
            .send(mvc, post("/api/v1/conversations"), customer, "{\"sellerId\":\"" + seller.getId() + "\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String path = "/api/v1/conversations/" + json.readTree(opened).at("/data/id").asText() + "/messages";
    support.send(mvc, post(path).header("Idempotency-Key", UUID.randomUUID().toString()), customer, "{\"imageUrl\":\"https://evil.com/a.jpg\"}")
        .andExpect(status().isBadRequest());
    support.send(mvc, post(path).header("Idempotency-Key", UUID.randomUUID().toString()), customer, "{\"imageUrl\":\"" + OWN_IMAGE + "\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.imageUrl").value(OWN_IMAGE));
  }

  private String productJson(String imageUrl) {
    return "{\"name\":\"P\",\"description\":\"d\",\"imageUrl\":\"" + imageUrl + "\",\"price\":\"10.00\",\"stock\":1,\"categoryId\":\"" + category.getId() + "\"}";
  }
}
