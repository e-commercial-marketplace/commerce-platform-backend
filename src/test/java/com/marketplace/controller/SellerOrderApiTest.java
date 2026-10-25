package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.marketplace.support.TestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử xử lý đơn phía seller theo features/order/API_CONTRACT.md: seller chỉ thấy và chỉ sửa dòng
 * hàng của mình; huỷ trả kho đúng một lần, tính lại tổng order và payment đang chờ; huỷ hết dòng thì
 * order CANCELLED và payment FAILED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerOrderApiTest {

  private static final String ITEMS = "/api/v1/seller/order-items";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;

  private User customer;
  private User sellerA;
  private User sellerB;
  private Product productA; // 2 x 50.00 = 100.00
  private Product productB; // 3 x 10.00 = 30.00
  private String orderId;

  /** Mỗi test bắt đầu với một order gồm một dòng của seller A và một dòng của seller B. */
  @BeforeEach
  void placeOrder() throws Exception {
    customer = support.user(Role.CUSTOMER, null);
    sellerA = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop A");
    sellerB = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop B");
    Category category = support.category();
    productA = support.product(sellerA, category, "Phone", "50.00", 10);
    productB = support.product(sellerB, category, "Case", "10.00", 10);
    orderId = checkout(addToCart(productA, 2), addToCart(productB, 3));
  }

  // ---------- xem dòng hàng ----------

  @Test
  void eachSellerSeesOnlyTheirOwnLines() throws Exception {
    list(sellerA, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
        .andExpect(jsonPath("$.data.content[0].productName").value("Phone"))
        .andExpect(jsonPath("$.data.content[0].quantity").value(2))
        .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.content[0].sellerId").value(sellerA.getId().toString()));
    list(sellerB, "").andExpect(jsonPath("$.data.content[0].productName").value("Case"));
  }

  @Test
  void listFiltersByStatusAndOrderAndRejectsBadValues() throws Exception {
    String line = lineOf(sellerA);
    confirm(sellerA, line).andExpect(status().isOk());

    list(sellerA, "?status=CONFIRMED").andExpect(jsonPath("$.data.totalElements").value(1));
    list(sellerA, "?status=PENDING").andExpect(jsonPath("$.data.totalElements").value(0));
    list(sellerA, "?orderId=" + orderId).andExpect(jsonPath("$.data.totalElements").value(1));
    list(sellerA, "?orderId=" + UUID.randomUUID()).andExpect(jsonPath("$.data.totalElements").value(0));

    list(sellerA, "?status=SHIPPED").andExpect(status().isBadRequest());
    list(sellerA, "?orderId=not-a-uuid").andExpect(status().isBadRequest());
    list(sellerA, "?size=101").andExpect(status().isBadRequest());
  }

  // ---------- xác nhận ----------

  @Test
  void confirmMovesPendingToConfirmedOnlyOnce() throws Exception {
    String line = lineOf(sellerA);

    confirm(sellerA, line)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    confirm(sellerA, line)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void anotherSellersLineIs403AndAnUnknownLineIs404() throws Exception {
    String lineOfB = lineOf(sellerB);

    confirm(sellerA, lineOfB)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    cancel(sellerA, lineOfB, null).andExpect(status().isForbidden());
    confirm(sellerA, UUID.randomUUID().toString())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    // Dòng của B không bị đổi.
    list(sellerB, "").andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
  }

  // ---------- huỷ ----------

  @Test
  void cancellingOneLineRestoresStockAndRecalculatesTheOrder() throws Exception {
    cancel(sellerA, lineOf(sellerA), "Out of stock")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    // Kho của Phone trả lại đủ 10 (checkout đã trừ 2).
    assertThat(stockOf(productA)).isEqualTo(10);
    // Order vẫn PENDING, tổng còn dòng của B (30.00), payment đang chờ cũng còn 30.00.
    order()
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.totalAmount").value("30.00"))
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
        // Dòng đã huỷ vẫn giữ bản chụp để xem lịch sử.
        .andExpect(jsonPath("$.data.items[?(@.productName=='Phone')].status").value("CANCELLED"));
  }

  @Test
  void aConfirmedLineCanBeCancelledButOnlyOnce() throws Exception {
    String line = lineOf(sellerA);
    confirm(sellerA, line);

    cancel(sellerA, line, null).andExpect(status().isOk());
    // Huỷ lần hai bị từ chối, kho không bị cộng trả hai lần.
    cancel(sellerA, line, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    assertThat(stockOf(productA)).isEqualTo(10);
  }

  @Test
  void cancellingEveryLineCancelsTheOrderAndFailsThePayment() throws Exception {
    cancel(sellerA, lineOf(sellerA), null).andExpect(status().isOk());
    cancel(sellerB, lineOf(sellerB), "No longer sold").andExpect(status().isOk());

    order()
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.totalAmount").value("0.00"))
        .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
    assertThat(stockOf(productB)).isEqualTo(10);
  }

  @Test
  void aTooLongReasonIs400() throws Exception {
    cancel(sellerA, lineOf(sellerA), "x".repeat(1001)).andExpect(status().isBadRequest());
    assertThat(stockOf(productA)).isEqualTo(8); // không có gì thay đổi
  }

  // ---------- phân quyền ----------

  @Test
  void onlyApprovedSellersCanProcessLines() throws Exception {
    mvc.perform(get(ITEMS)).andExpect(status().isUnauthorized());
    // Customer và admin sai role: 403.
    list(customer, "").andExpect(status().isForbidden());
    list(support.user(Role.ADMIN, null), "").andExpect(status().isForbidden());
    // Seller chưa duyệt hoặc bị khoá: 403 (kiểm tra trong DB, token chỉ mang role).
    for (SellerStatus notApproved : new SellerStatus[] {SellerStatus.PENDING, SellerStatus.SUSPENDED}) {
      list(support.user(Role.SELLER, notApproved), "")
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  // ---------- hàm hỗ trợ ----------

  private int stockOf(Product product) {
    return products.findById(product.getId()).orElseThrow().getStock();
  }

  /** Id dòng hàng duy nhất của seller trong order của test. */
  private String lineOf(User seller) throws Exception {
    return JsonPath.read(
        list(seller, "").andReturn().getResponse().getContentAsString(), "$.data.content[0].id");
  }

  private ResultActions list(User who, String query) throws Exception {
    return mvc.perform(get(ITEMS + query).header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private ResultActions confirm(User who, String lineId) throws Exception {
    return mvc.perform(
        patch(ITEMS + "/" + lineId + "/confirm").header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private ResultActions cancel(User who, String lineId, String reason) throws Exception {
    String body = reason == null ? "{}" : "{\"cancelReason\":\"" + reason + "\"}";
    return support.send(mvc, patch(ITEMS + "/" + lineId + "/cancel"), who, body);
  }

  private ResultActions order() throws Exception {
    return mvc.perform(
        get("/api/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)));
  }

  private String addToCart(Product product, int quantity) throws Exception {
    return support.addToCart(mvc, customer, product, quantity);
  }

  private String checkout(String... cartItemIds) throws Exception {
    String ids = String.join("\",\"", List.of(cartItemIds));
    ResultActions result =
        support.send(
            mvc,
            post("/api/v1/orders"),
            customer,
            "{\"cartItemIds\":[\"" + ids + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"COD\"}");
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
