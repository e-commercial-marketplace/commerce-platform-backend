package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Carrier;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CarrierRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Seller tạo và giao shipment theo features/shipment/API_CONTRACT.md: luật đơn vị vận chuyển, luật dòng
 * hàng, chuyển trạng thái, trả kho, hoàn tất COD và order DELIVERED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipmentApiTest {

  private static final String SHIPMENTS = "/api/v1/seller/shipments";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private CarrierRepository carriers;
  @Autowired private ProductRepository products;
  @Autowired private JdbcTemplate jdbc;

  private User customer;
  private User sellerA;
  private User sellerB;
  private Product phone; // seller A, 100.00
  private Product phoneCase; // seller A, 20.00
  private Product charger; // seller B, 50.00
  private Carrier ghn;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    sellerA = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop A");
    sellerB = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop B");
    Category category = support.category();
    phone = support.product(sellerA, category, "Phone", "100.00", 10);
    phoneCase = support.product(sellerA, category, "Case", "20.00", 10);
    charger = support.product(sellerB, category, "Charger", "50.00", 10);
    ghn = carriers.save(Carrier.create("GHN " + UUID.randomUUID()));
  }

  // ---------- đơn vị vận chuyển theo cách giao ----------

  @Test
  void platformDeliveryNeedsAnActiveCarrierAndSelfDeliveryHasNone() throws Exception {
    String body = checkout("COD", addToCart(phone, 1));
    String order = orderIdOf(body);
    String line = confirmedLine(body, sellerA);
    Carrier off = carriers.save(Carrier.create("Off " + UUID.randomUUID()));
    off.setActive(false);
    carriers.save(off);

    create(sellerA, order, "PLATFORM_DELIVERY", null, line).andExpect(status().isBadRequest());
    create(sellerA, order, "PLATFORM_DELIVERY", off.getId(), line).andExpect(status().isBadRequest());
    create(sellerA, order, "PLATFORM_DELIVERY", UUID.randomUUID(), line).andExpect(status().isBadRequest());
    create(sellerA, order, "SELF_DELIVERY", ghn.getId(), line).andExpect(status().isBadRequest());

    create(sellerA, order, "PLATFORM_DELIVERY", ghn.getId(), line)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.carrier.name").value(ghn.getName()))
        .andExpect(jsonPath("$.data.shippingFee").value("0.00"))
        .andExpect(jsonPath("$.data.items[0].orderItemId").value(line))
        .andExpect(jsonPath("$.data.items[0].quantity").value(1));
  }

  @Test
  void selfDeliveryNeedsNoCarrier() throws Exception {
    String body = checkout("COD", addToCart(phone, 1));
    create(sellerA, orderIdOf(body), "SELF_DELIVERY", null, confirmedLine(body, sellerA))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.deliveryMethod").value("SELF_DELIVERY"))
        .andExpect(jsonPath("$.data.carrier").isEmpty());
  }

  // ---------- luật dòng hàng ----------

  @Test
  void onlyOwnConfirmedLinesOfOneOrderCanBeShippedOnce() throws Exception {
    String body = checkout("COD", addToCart(phone, 1), addToCart(phoneCase, 1), addToCart(charger, 1));
    String order = orderIdOf(body);
    List<String> linesOfA = linesOf(body, sellerA);
    String lineOfB = linesOf(body, sellerB).get(0);

    create(sellerA, order, "SELF_DELIVERY", null, linesOfA.get(0)).andExpect(status().isBadRequest()); // chưa CONFIRMED
    confirm(sellerA, linesOfA.get(0));
    confirm(sellerA, linesOfA.get(1));
    confirm(sellerB, lineOfB);
    create(sellerA, order, "SELF_DELIVERY", null, lineOfB).andExpect(status().isForbidden()); // của seller B
    create(sellerA, order, "SELF_DELIVERY", null, UUID.randomUUID().toString()).andExpect(status().isNotFound());

    String otherOrder = orderIdOf(checkout("COD", addToCart(phone, 1)));
    create(sellerA, otherOrder, "SELF_DELIVERY", null, linesOfA.get(0)).andExpect(status().isBadRequest()); // khác order

    create(sellerA, order, "SELF_DELIVERY", null, linesOfA.get(0)).andExpect(status().isCreated());
    create(sellerA, order, "SELF_DELIVERY", null, linesOfA.get(0)).andExpect(status().isBadRequest()); // đã gán
    // Mỗi seller chỉ một shipment cho mỗi order, kể cả với dòng chưa gán.
    create(sellerA, order, "SELF_DELIVERY", null, linesOfA.get(1)).andExpect(status().isBadRequest());
  }

  // ---------- giao hàng, COD và order DELIVERED ----------

  @Test
  void aCodOrderIsDeliveredAndCollectedOnlyWhenEverySellerDelivered() throws Exception {
    String body = checkout("COD", addToCart(phone, 1), addToCart(charger, 1));
    String order = orderIdOf(body);
    String shipA = idOf(create(sellerA, order, "SELF_DELIVERY", null, confirmedLine(body, sellerA)));
    String shipB = idOf(create(sellerB, order, "PLATFORM_DELIVERY", ghn.getId(), confirmedLine(body, sellerB)));

    move(sellerA, shipA, "PREPARING", null).andExpect(status().isOk());
    move(sellerA, shipA, "SHIPPED", null).andExpect(status().isBadRequest()); // thiếu mã vận đơn
    move(sellerA, shipA, "DELIVERED", null).andExpect(status().isBadRequest()); // nhảy bước
    move(sellerA, shipA, "SHIPPED", "TRACK-A")
        .andExpect(jsonPath("$.data.trackingCode").value("TRACK-A"))
        .andExpect(jsonPath("$.data.shippedAt").isNotEmpty());
    move(sellerA, shipA, "DELIVERED", null).andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());
    order(order)
        .andExpect(jsonPath("$.data.status").value("PENDING")) // seller B chưa giao
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

    move(sellerB, shipB, "PREPARING", null);
    move(sellerB, shipB, "SHIPPED", "TRACK-B");
    move(sellerB, shipB, "DELIVERED", null).andExpect(status().isOk());
    order(order)
        .andExpect(jsonPath("$.data.status").value("DELIVERED"))
        .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.shipments.length()").value(2))
        .andExpect(jsonPath("$.data.shipments[?(@.deliveryMethod=='PLATFORM_DELIVERY')].carrier.name")
            .value(ghn.getName()));

    move(sellerA, shipA, "CANCELLED", null).andExpect(status().isBadRequest()); // đã giao, không quay lại
    move(sellerB, shipA, "PREPARING", null).andExpect(status().isForbidden()); // shipment của seller khác
  }

  @Test
  void aFailedShipmentKeepsStockAndReturnedRestoresItOnce() throws Exception {
    String body = checkout("COD", addToCart(phone, 2));
    String order = orderIdOf(body);
    String ship = idOf(create(sellerA, order, "SELF_DELIVERY", null, confirmedLine(body, sellerA)));
    assertThat(stockOf(phone)).isEqualTo(8);

    move(sellerA, ship, "PREPARING", null);
    move(sellerA, ship, "SHIPPED", "TRACK-1");
    move(sellerA, ship, "FAILED", null).andExpect(status().isOk());
    assertThat(stockOf(phone)).isEqualTo(8); // FAILED không trả kho

    move(sellerA, ship, "RETURNED", null).andExpect(status().isOk());
    assertThat(stockOf(phone)).isEqualTo(10);
    move(sellerA, ship, "RETURNED", null).andExpect(status().isBadRequest());
    assertThat(stockOf(phone)).isEqualTo(10); // trả đúng một lần
    order(order).andExpect(jsonPath("$.data.status").value("PENDING")); // không DELIVERED
  }

  // ---------- huỷ ----------

  @Test
  void cancellingAShipmentCancelsItsLinesRestoresStockAndRecalculatesTheOrder() throws Exception {
    String body = checkout("COD", addToCart(phone, 1), addToCart(charger, 1));
    String order = orderIdOf(body);
    String ship = idOf(create(sellerA, order, "SELF_DELIVERY", null, confirmedLine(body, sellerA)));

    move(sellerA, ship, "CANCELLED", null).andExpect(status().isOk());
    assertThat(stockOf(phone)).isEqualTo(10);
    order(order)
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.totalAmount").value("50.00"))
        .andExpect(jsonPath("$.data.items[?(@.sellerId=='" + sellerA.getId() + "')].status").value("CANCELLED"));
  }

  @Test
  void aShipmentOfAPaidVnpayOrderCannotBeCancelled() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String order = orderIdOf(body);
    jdbc.update("update orders set status = 'PAID' where id = ?", UUID.fromString(order)); // như đã trả tiền
    String ship = idOf(create(sellerA, order, "SELF_DELIVERY", null, confirmedLine(body, sellerA)));

    move(sellerA, ship, "CANCELLED", null).andExpect(status().isBadRequest());
    assertThat(stockOf(phone)).isEqualTo(9);
  }

  @Test
  void aLineInAShipmentCannotBeCancelledDirectly() throws Exception {
    String body = checkout("COD", addToCart(phone, 1));
    String line = confirmedLine(body, sellerA);
    create(sellerA, orderIdOf(body), "SELF_DELIVERY", null, line);

    support.send(mvc, patch("/api/v1/seller/order-items/" + line + "/cancel"), sellerA, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CANNOT_CANCEL_AFTER_SHIPMENT"));
  }

  @Test
  void sellersListOnlyTheirOwnShipments() throws Exception {
    String body = checkout("COD", addToCart(phone, 1), addToCart(charger, 1));
    String order = orderIdOf(body);
    String shipA = idOf(create(sellerA, order, "SELF_DELIVERY", null, confirmedLine(body, sellerA)));
    create(sellerB, order, "SELF_DELIVERY", null, confirmedLine(body, sellerB));

    mvc.perform(get(SHIPMENTS + "?orderId=" + order).header(HttpHeaders.AUTHORIZATION, support.bearer(sellerA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(shipA));
    mvc.perform(get(SHIPMENTS + "?status=DELIVERED&orderId=" + order)
            .header(HttpHeaders.AUTHORIZATION, support.bearer(sellerA)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(get(SHIPMENTS).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isForbidden());
  }

  // ---------- hàm hỗ trợ ----------

  private ResultActions create(User seller, String orderId, String method, UUID carrierId, String... lineIds)
      throws Exception {
    String carrier = carrierId == null ? "null" : "\"" + carrierId + "\"";
    String lines = String.join("\",\"", lineIds);
    return support.send(
        mvc,
        post(SHIPMENTS),
        seller,
        "{\"orderId\":\"" + orderId + "\",\"deliveryMethod\":\"" + method + "\",\"carrierId\":" + carrier
            + ",\"orderItemIds\":[\"" + lines + "\"]}");
  }

  private ResultActions move(User seller, String shipmentId, String status, String trackingCode)
      throws Exception {
    String tracking = trackingCode == null ? "null" : "\"" + trackingCode + "\"";
    return support.send(
        mvc,
        patch(SHIPMENTS + "/" + shipmentId + "/status"),
        seller,
        "{\"status\":\"" + status + "\",\"trackingCode\":" + tracking + "}");
  }

  private void confirm(User seller, String lineId) throws Exception {
    mvc.perform(
            patch("/api/v1/seller/order-items/" + lineId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isOk());
  }

  /** Dòng đầu tiên của seller trong order, đã được xác nhận. */
  private String confirmedLine(String body, User seller) throws Exception {
    String line = linesOf(body, seller).get(0);
    confirm(seller, line);
    return line;
  }

  private ResultActions order(String orderId) throws Exception {
    return mvc.perform(
        get("/api/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)));
  }

  private String addToCart(Product product, int quantity) throws Exception {
    ResultActions result =
        support.send(
            mvc,
            post("/api/v1/cart/items"),
            customer,
            "{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity + "}");
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
  }

  private String checkout(String method, String... cartItemIds) throws Exception {
    String ids = String.join("\",\"", List.of(cartItemIds));
    return support
        .send(
            mvc,
            post("/api/v1/orders"),
            customer,
            "{\"cartItemIds\":[\"" + ids + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"" + method + "\"}")
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> linesOf(String body, User seller) {
    return JsonPath.read(body, "$.data.items[?(@.sellerId=='" + seller.getId() + "')].id");
  }

  private static String orderIdOf(String body) {
    return JsonPath.read(body, "$.data.id");
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }

  private int stockOf(Product product) {
    return products.findById(product.getId()).orElseThrow().getStock();
  }
}
