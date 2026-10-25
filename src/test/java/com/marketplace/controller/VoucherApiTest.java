package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.VoucherRepository;
import com.marketplace.support.TestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Voucher theo features/voucher/API_CONTRACT.md: admin quản lý, khách kiểm tra mã, áp lúc checkout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoucherApiTest {

  private static final String ADMIN = "/api/v1/admin/vouchers";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;
  // Spy: repository thật, chỉ giả kết quả existsById trong test tạo trùng mã song song.
  @MockitoSpyBean private VoucherRepository voucherRepository;

  private User admin;
  private User customer;
  private User sellerA;
  private User sellerB;
  private Product phone; // seller A, 59.00
  private Product charger; // seller B, 50.00

  @BeforeEach
  void setUp() {
    admin = support.user(Role.ADMIN, null);
    customer = support.user(Role.CUSTOMER, null);
    sellerA = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop A");
    sellerB = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop B");
    Category category = support.category();
    phone = support.product(sellerA, category, "Phone", "59.00", 10);
    charger = support.product(sellerB, category, "Charger", "50.00", 10);
  }

  // ---------- admin ----------

  @Test
  void adminCreatesAVoucherWithANormalisedCodeAndRejectsBadInput() throws Exception {
    String raw = "  sale-" + suffix() + "  ";
    String code = raw.trim().toUpperCase();
    create(raw, 10, Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofDays(7)), 100)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.code").value(code))
        .andExpect(jsonPath("$.data.timesUsed").value(0));

    // Trùng mã (khác hoa thường), sai khung giờ, phần trăm 0, ký tự lạ: đều 400.
    create(code.toLowerCase(), 10, Instant.now(), Instant.now().plusSeconds(60), 1).andExpect(status().isBadRequest());
    Instant t = Instant.now();
    create("X" + suffix(), 10, t, t, 1).andExpect(status().isBadRequest());
    create("X" + suffix(), 0, t, t.plusSeconds(60), 1).andExpect(status().isBadRequest());
    create("A B", 10, t, t.plusSeconds(60), 1).andExpect(status().isBadRequest());
  }

  @Test
  void adminUpdatesAndExpiresAVoucher() throws Exception {
    String code = activeVoucher(10, 5);
    update(code.toLowerCase(), "{\"discountPercent\":15,\"usageLimit\":20}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.discountPercent").value(15))
        .andExpect(jsonPath("$.data.usageLimit").value(20));
    update(code, "{\"validTo\":\"2000-01-01T00:00:00Z\"}").andExpect(status().isBadRequest()); // trước validFrom
    update("NOPE" + suffix(), "{\"usageLimit\":5}").andExpect(status().isNotFound());

    asAdmin(delete(ADMIN + "/" + code)).andExpect(status().isOk());
    validate(customer, code)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VOUCHER_EXPIRED"));

    // Voucher chưa bắt đầu vẫn hết hạn được: validFrom lùi về trước validTo.
    String future = "F" + suffix();
    create(future, 10, Instant.now().plus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(2)), 1);
    String body = asAdmin(delete(ADMIN + "/" + future)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    Instant from = Instant.parse(JsonPath.read(body, "$.data.validFrom"));
    Instant to = Instant.parse(JsonPath.read(body, "$.data.validTo"));
    assertThat(from).isBefore(to);
  }

  @Test
  void theDiscountOfAUsedVoucherCannotChange() throws Exception {
    String code = activeVoucher(10, 5);
    checkout(code, addToCart(phone, 1)).andExpect(status().isCreated()); // timesUsed = 1

    update(code, "{\"discountPercent\":50}").andExpect(status().isBadRequest());
    update(code, "{\"discountPercent\":10,\"usageLimit\":9}") // giữ nguyên % thì vẫn sửa được trường khác
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.discountPercent").value(10))
        .andExpect(jsonPath("$.data.usageLimit").value(9));
  }

  @Test
  void onlyAdminsManageVouchers() throws Exception {
    for (User who : new User[] {customer, sellerA}) {
      mvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, support.bearer(who)))
          .andExpect(status().isForbidden());
    }
    validate(sellerA, "ANY").andExpect(status().isForbidden()); // kiểm tra mã chỉ dành cho customer
    asAdmin(get(ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
  }

  @Test
  void aDuplicateCodeThatSlipsPastTheExistsCheckIsStill400() throws Exception {
    String code = "RACE" + suffix();
    Instant from = Instant.now().minusSeconds(60);
    Instant to = Instant.now().plus(Duration.ofDays(1));
    create(code, 10, from, to, 5).andExpect(status().isCreated());
    // Giả lúc hai admin tạo cùng mã song song: bước kiểm tra trước "chưa thấy" mã, nên request này đi tới
    // INSERT thật và DB bật lỗi khoá chính. Lỗi đó phải thành 400, không phải 500.
    doReturn(false).when(voucherRepository).existsById(code);
    create(code, 20, from, to, 5)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    // Voucher có sẵn không bị ghi đè.
    assertThat(voucherRepository.findById(code).orElseThrow().getDiscountPercent()).isEqualTo(10);
  }

  // ---------- khách kiểm tra mã ----------

  @Test
  void validationReportsWhyAVoucherCannotBeUsed() throws Exception {
    String code = activeVoucher(10, 1);
    validate(customer, code.toLowerCase()).andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value(code));
    validate(customer, "NOPE" + suffix()).andExpect(status().isNotFound());

    String future = "F" + suffix();
    create(future, 10, Instant.now().plus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(2)), 1);
    validate(customer, future).andExpect(jsonPath("$.error.code").value("VOUCHER_EXPIRED"));

    checkout(code, addToCart(phone, 1)).andExpect(status().isCreated()); // dùng hết lượt duy nhất
    validate(customer, code).andExpect(jsonPath("$.error.code").value("VOUCHER_EXHAUSTED"));
  }

  // ---------- áp lúc checkout ----------

  @Test
  void checkoutAppliesThePercentageAndConsumesOneUse() throws Exception {
    String code = activeVoucher(10, 5);
    checkout(code.toLowerCase(), addToCart(phone, 2)) // 2 x 59.00 = 118.00
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.voucherCode").value(code))
        .andExpect(jsonPath("$.data.discountAmount").value("11.80"))
        .andExpect(jsonPath("$.data.totalAmount").value("106.20"));
    validate(customer, code).andExpect(jsonPath("$.data.timesUsed").value(1));
  }

  @Test
  void anExhaustedVoucherRejectsTheWholeCheckout() throws Exception {
    String code = activeVoucher(10, 1);
    checkout(code, addToCart(phone, 1)).andExpect(status().isCreated());

    String line = addToCart(phone, 1);
    checkout(code, line)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VOUCHER_EXHAUSTED"));
    assertThat(products.findById(phone.getId()).orElseThrow().getStock()).isEqualTo(9); // chỉ đơn đầu trừ kho
    checkout("NOPE" + suffix(), line).andExpect(status().isNotFound());
  }

  @Test
  void cancellingALineRecalculatesTheDiscountOnWhatIsLeft() throws Exception {
    String code = activeVoucher(10, 5);
    String body = checkout(code, addToCart(phone, 1), addToCart(charger, 1)) // 59.00 + 50.00 = 109.00
        .andExpect(jsonPath("$.data.discountAmount").value("10.90"))
        .andExpect(jsonPath("$.data.totalAmount").value("98.10"))
        .andReturn().getResponse().getContentAsString();
    List<String> lineOfA = JsonPath.read(body, "$.data.items[?(@.sellerId=='" + sellerA.getId() + "')].id");

    support.send(mvc, patch("/api/v1/seller/order-items/" + lineOfA.get(0) + "/cancel"), sellerA, "{}")
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/orders/" + JsonPath.read(body, "$.data.id"))
            .header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data.discountAmount").value("5.00")) // 10% của 50.00 còn lại
        .andExpect(jsonPath("$.data.totalAmount").value("45.00"));
  }

  // ---------- hàm hỗ trợ ----------

  /** Voucher đang dùng được, mã ngẫu nhiên (DB test dùng chung giữa các test). */
  private String activeVoucher(int percent, int usageLimit) throws Exception {
    String code = "V" + suffix();
    create(code, percent, Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofDays(7)), usageLimit)
        .andExpect(status().isCreated());
    return code;
  }

  private ResultActions create(String code, int percent, Instant from, Instant to, int usageLimit)
      throws Exception {
    return support.send(
        mvc,
        post(ADMIN),
        admin,
        "{\"code\":\"%s\",\"description\":\"d\",\"discountPercent\":%d,\"validFrom\":\"%s\",\"validTo\":\"%s\",\"usageLimit\":%d}"
            .formatted(code, percent, from, to, usageLimit));
  }

  private ResultActions update(String code, String json) throws Exception {
    return support.send(mvc, patch(ADMIN + "/" + code), admin, json);
  }

  private ResultActions asAdmin(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, support.bearer(admin)));
  }

  private ResultActions validate(User who, String code) throws Exception {
    return mvc.perform(
        get("/api/v1/vouchers/" + code + "/validate").header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private String addToCart(Product product, int quantity) throws Exception {
    return support.addToCart(mvc, customer, product, quantity);
  }

  private ResultActions checkout(String voucherCode, String... cartItemIds) throws Exception {
    String ids = String.join("\",\"", List.of(cartItemIds));
    return support.send(
        mvc,
        post("/api/v1/orders"),
        customer,
        "{\"cartItemIds\":[\"" + ids + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"COD\",\"voucherCode\":\""
            + voucherCode + "\"}");
  }

  private static String suffix() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
