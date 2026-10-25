package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.OrderService;
import com.marketplace.support.TestSupport;
import com.marketplace.vnpay.VnpayGateway;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Kiểm thử thanh toán VNPAY theo features/payment/API_CONTRACT.md mà không gọi VNPAY thật: test tự dựng
 * bộ tham số {@code vnp_*} giống VNPAY gửi về và ký bằng khoá bí mật của profile test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTest {

  private static final String IPN = "/api/v1/payments/vnpay/ipn";
  private static final String RETURN = "/api/v1/payments/vnpay/return";
  private static final String RETRY = "/api/v1/payments/vnpay/create";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private VnpayGateway gateway;
  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orders;
  @Autowired private ProductRepository products;
  @Autowired private JdbcTemplate jdbc;

  private User customer;
  private User sellerA;
  private User sellerB;
  private Product phone; // 100000.00, kho 10
  private Product charger; // 50000.00, kho 10

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    sellerA = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop A");
    sellerB = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop B");
    Category category = support.category();
    phone = support.product(sellerA, category, "Phone", "100000.00", 10);
    charger = support.product(sellerB, category, "Charger", "50000.00", 10);
  }

  // ---------- checkout VNPAY ----------

  @Test
  void checkoutWithVnpayReturnsASignedPaymentUrl() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 2));

    assertThat(JsonPath.<String>read(body, "$.data.paymentMethod")).isEqualTo("VNPAY");
    assertThat(JsonPath.<String>read(body, "$.data.paymentStatus")).isEqualTo("PENDING");
    String url = JsonPath.read(body, "$.data.paymentUrl");
    assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
    Map<String, String> params = queryOf(url);
    assertThat(gateway.verify(params)).isTrue(); // link do chính backend ký
    assertThat(params).containsEntry("vnp_Amount", "20000000").containsEntry("vnp_TmnCode", "TESTCODE");
    assertThat(params.get("vnp_TxnRef")).hasSize(32);

    // Xem lại order thì không còn link; order COD cũng không có link.
    order(orderIdOf(body)).andExpect(jsonPath("$.data.paymentUrl").isEmpty());
    String cod = checkout("COD", addToCart(charger, 1));
    assertThat(JsonPath.<Object>read(cod, "$.data.paymentUrl")).isNull();
  }

  // ---------- kết quả VNPAY (IPN) ----------

  @Test
  void aSuccessfulIpnMarksTheOrderPaidAndARepeatChangesNothing() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    Map<String, String> result = vnpayResult(paymentUrlOf(body), "00");

    ipn(result).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
    order(orderIdOf(body))
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED"));

    ipn(result).andExpect(jsonPath("$.RspCode").value("02")); // VNPAY gửi lại: đã xử lý
    order(orderIdOf(body)).andExpect(jsonPath("$.data.status").value("PAID"));
  }

  @Test
  void aFailedPaymentKeepsTheOrderPayableAndARetryCreatesANewAttempt() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String orderId = orderIdOf(body);
    String firstUrl = paymentUrlOf(body);

    ipn(vnpayResult(firstUrl, "24")).andExpect(jsonPath("$.RspCode").value("00")); // khách huỷ
    order(orderId)
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));

    String retry = retry(customer, orderId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String secondUrl = JsonPath.read(retry, "$.data.paymentUrl");
    assertThat(JsonPath.<String>read(retry, "$.data.status")).isEqualTo("PENDING");
    assertThat(JsonPath.<String>read(retry, "$.data.amount")).isEqualTo("100000.00");
    assertThat(queryOf(secondUrl).get("vnp_TxnRef")).isNotEqualTo(queryOf(firstUrl).get("vnp_TxnRef"));
    order(orderId).andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));

    ipn(vnpayResult(secondUrl, "00")).andExpect(jsonPath("$.RspCode").value("00"));
    order(orderId)
        .andExpect(jsonPath("$.data.status").value("PAID"))
        .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED")); // dù lần đầu đã FAILED
  }

  @Test
  void aRetryInvalidatesThePreviousLink() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String firstUrl = paymentUrlOf(body);
    retry(customer, orderIdOf(body)).andExpect(status().isOk());

    // Khách lỡ trả bằng link cũ: không đổi trạng thái order (backend chỉ ghi log để hoàn tiền tay).
    ipn(vnpayResult(firstUrl, "00")).andExpect(jsonPath("$.RspCode").value("02"));
    order(orderIdOf(body)).andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  @Test
  void invalidResultsChangeNothing() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    Map<String, String> result = vnpayResult(paymentUrlOf(body), "00");

    Map<String, String> badSignature = new TreeMap<>(result);
    badSignature.put("vnp_ResponseCode", "99"); // đổi dữ liệu sau khi ký
    ipn(badSignature).andExpect(jsonPath("$.RspCode").value("97"));

    Map<String, String> unknownRef = new TreeMap<>(result);
    unknownRef.put("vnp_TxnRef", "doesnotexist");
    ipn(resign(unknownRef)).andExpect(jsonPath("$.RspCode").value("01"));

    Map<String, String> wrongAmount = new TreeMap<>(result);
    wrongAmount.put("vnp_Amount", "100");
    ipn(resign(wrongAmount)).andExpect(jsonPath("$.RspCode").value("04"));

    order(orderIdOf(body))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
  }

  // ---------- Return URL ----------

  @Test
  void theReturnUrlRecordsTheResultAndRedirectsToTheApp() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String orderId = orderIdOf(body);
    Map<String, String> result = vnpayResult(paymentUrlOf(body), "00");

    callback(RETURN, result)
        .andExpect(status().isFound())
        .andExpect(
            header().string(HttpHeaders.LOCATION, "marketplace://payment-result?orderId=" + orderId + "&status=COMPLETED"));
    order(orderId).andExpect(jsonPath("$.data.status").value("PAID"));
    ipn(result).andExpect(jsonPath("$.RspCode").value("02")); // IPN tới sau: đã ghi nhận

    Map<String, String> forged = new TreeMap<>(result);
    forged.put(VnpayGateway.SECURE_HASH, "abc");
    callback(RETURN, forged)
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "marketplace://payment-result?status=INVALID"));
  }

  // ---------- quy tắc thanh toán lại ----------

  @Test
  void onlyTheOwnerCanRetryAnUnpaidVnpayOrder() throws Exception {
    String vnpay = orderIdOf(checkout("VNPAY", addToCart(phone, 1)));
    String cod = orderIdOf(checkout("COD", addToCart(charger, 1)));

    User other = support.user(Role.CUSTOMER, null);
    retry(other, vnpay).andExpect(status().isNotFound());
    retry(customer, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    retry(customer, cod)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_PAYABLE"));
    support.send(mvc, post(RETRY), customer, "{}").andExpect(status().isBadRequest());
    retry(sellerA, vnpay).andExpect(status().isForbidden());
    mvc.perform(post(RETRY).contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + vnpay + "\"}"))
        .andExpect(status().isUnauthorized());

    ipn(vnpayResult(paymentUrlOf(retryBody(vnpay)), "00"));
    retry(customer, vnpay)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_PAYABLE")); // đã trả rồi
  }

  // ---------- seller huỷ dòng ----------

  @Test
  void aCancelledLineInvalidatesThePendingLinkAndAPaidOrderCannotBeCancelled() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1), addToCart(charger, 1));
    String orderId = orderIdOf(body);
    String phoneLine = lineOf(body, sellerA);
    String chargerLine = lineOf(body, sellerB);

    cancel(sellerA, phoneLine).andExpect(status().isOk());
    order(orderId)
        .andExpect(jsonPath("$.data.totalAmount").value("50000.00"))
        .andExpect(jsonPath("$.data.paymentStatus").value("FAILED")); // link cũ mang số tiền cũ

    String retry = retryBody(orderId);
    assertThat(JsonPath.<String>read(retry, "$.data.amount")).isEqualTo("50000.00");
    assertThat(queryOf(paymentUrlOf(retry))).containsEntry("vnp_Amount", "5000000");
    ipn(vnpayResult(paymentUrlOf(retry), "00")).andExpect(jsonPath("$.RspCode").value("00"));

    cancel(sellerB, chargerLine)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CANNOT_CANCEL_AFTER_PAYMENT"));
  }

  // ---------- seller xác nhận (FR-FUL-05) ----------

  @Test
  void aSellerConfirmsALineOfAVnpayOrderOnlyAfterItIsPaid() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String line = lineOf(body, sellerA);

    confirm(sellerA, line)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR")); // chưa trả tiền

    ipn(vnpayResult(paymentUrlOf(body), "00")).andExpect(jsonPath("$.RspCode").value("00"));
    confirm(sellerA, line)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
  }

  @Test
  void aSellerConfirmsALineOfACodOrderRightAway() throws Exception {
    String body = checkout("COD", addToCart(charger, 1));
    confirm(sellerB, lineOf(body, sellerB)).andExpect(status().isOk());
  }

  // ---------- hạn 24 giờ ----------

  @Test
  void anExpiredVnpayOrderIsCancelledAndItsStockRestoredOnce() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 2));
    UUID orderId = UUID.fromString(orderIdOf(body));
    Instant createdAt = Instant.parse(JsonPath.read(body, "$.data.createdAt"));
    UUID codOrder = UUID.fromString(orderIdOf(checkout("COD", addToCart(charger, 1))));
    assertThat(stockOf(phone)).isEqualTo(8);

    // Job chỉ chọn order VNPAY chưa trả, không chọn order COD.
    List<UUID> expired = orders.findExpiredPrepaymentOrderIds(Instant.now().plus(Duration.ofMinutes(1)));
    assertThat(expired).contains(orderId).doesNotContain(codOrder);

    assertThat(orderService.cancelExpiredOrder(orderId, createdAt.plus(Duration.ofHours(23)))).isFalse();
    assertThat(orderService.cancelExpiredOrder(orderId, createdAt.plus(Duration.ofHours(25)))).isTrue();
    assertThat(orderService.cancelExpiredOrder(orderId, createdAt.plus(Duration.ofHours(26)))).isFalse();
    assertThat(orderService.cancelExpiredOrder(codOrder, createdAt.plus(Duration.ofHours(25)))).isFalse();

    assertThat(stockOf(phone)).isEqualTo(10); // trả kho đúng một lần
    order(orderId.toString())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.totalAmount").value("0.00"))
        .andExpect(jsonPath("$.data.items[0].status").value("CANCELLED"));
    retry(customer, orderId.toString()).andExpect(status().isConflict());
  }

  @Test
  void aRetryAfterTheDeadlineIsRefused() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    String orderId = orderIdOf(body);
    ipn(vnpayResult(paymentUrlOf(body), "24")); // lần đầu thất bại, order vẫn PENDING

    // Lùi giờ đặt hàng 25 giờ để order quá hạn nhưng job chưa kịp huỷ.
    jdbc.update(
        "update orders set created_at = ? where id = ?",
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(25),
        UUID.fromString(orderId));

    retry(customer, orderId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_PAYABLE"));
  }

  @Test
  void aPaidOrderIsNotCancelledByTheDeadline() throws Exception {
    String body = checkout("VNPAY", addToCart(phone, 1));
    UUID orderId = UUID.fromString(orderIdOf(body));
    Instant createdAt = Instant.parse(JsonPath.read(body, "$.data.createdAt"));
    ipn(vnpayResult(paymentUrlOf(body), "00"));

    assertThat(orderService.cancelExpiredOrder(orderId, createdAt.plus(Duration.ofHours(25)))).isFalse();
    order(orderId.toString()).andExpect(jsonPath("$.data.status").value("PAID"));
  }

  // ---------- hàm hỗ trợ ----------

  /**
   * Dựng kết quả VNPAY cho link thanh toán: lấy mã tham chiếu và số tiền từ link, thêm mã phản hồi rồi
   * ký bằng khoá bí mật test (giống VNPAY ký thật).
   */
  private Map<String, String> vnpayResult(String paymentUrl, String responseCode) {
    Map<String, String> link = queryOf(paymentUrl);
    Map<String, String> result = new TreeMap<>();
    result.put("vnp_TmnCode", link.get("vnp_TmnCode"));
    result.put("vnp_TxnRef", link.get("vnp_TxnRef"));
    result.put("vnp_Amount", link.get("vnp_Amount"));
    result.put("vnp_OrderInfo", link.get("vnp_OrderInfo"));
    result.put("vnp_ResponseCode", responseCode);
    result.put("vnp_TransactionStatus", responseCode);
    result.put("vnp_TransactionNo", "14000000");
    result.put("vnp_BankCode", "NCB");
    return resign(result);
  }

  /** Ký lại bộ tham số (bỏ chữ ký cũ nếu có). */
  private Map<String, String> resign(Map<String, String> params) {
    Map<String, String> unsigned = new TreeMap<>(params);
    unsigned.remove(VnpayGateway.SECURE_HASH);
    Map<String, String> signed = new TreeMap<>(unsigned);
    signed.put(VnpayGateway.SECURE_HASH, gateway.sign(VnpayGateway.canonicalQuery(unsigned)));
    return signed;
  }

  /** Các tham số trong query của link (đã giải mã URL). */
  private static Map<String, String> queryOf(String url) {
    Map<String, String> params = new TreeMap<>();
    UriComponentsBuilder.fromUriString(url)
        .build()
        .getQueryParams()
        .forEach((key, values) -> params.put(key, URLDecoder.decode(values.get(0), StandardCharsets.UTF_8)));
    return params;
  }

  private ResultActions ipn(Map<String, String> params) throws Exception {
    return callback(IPN, params);
  }

  private ResultActions callback(String path, Map<String, String> params) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    params.forEach((key, value) -> request.param(key, value));
    return mvc.perform(request);
  }

  private ResultActions retry(User who, String orderId) throws Exception {
    return support.send(mvc, post(RETRY), who, "{\"orderId\":\"" + orderId + "\"}");
  }

  private String retryBody(String orderId) throws Exception {
    return retry(customer, orderId).andReturn().getResponse().getContentAsString();
  }

  private ResultActions confirm(User seller, String lineId) throws Exception {
    return mvc.perform(
        patch("/api/v1/seller/order-items/" + lineId + "/confirm")
            .header(HttpHeaders.AUTHORIZATION, support.bearer(seller)));
  }

  private ResultActions cancel(User seller, String lineId) throws Exception {
    return support.send(mvc, patch("/api/v1/seller/order-items/" + lineId + "/cancel"), seller, "{}");
  }

  private ResultActions order(String orderId) throws Exception {
    return mvc.perform(
        get("/api/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)));
  }

  private String addToCart(Product product, int quantity) throws Exception {
    return support.addToCart(mvc, customer, product, quantity);
  }

  /** Checkout các dòng giỏ, trả nguyên body của response (201). */
  private String checkout(String method, String... cartItemIds) throws Exception {
    String ids = String.join("\",\"", List.of(cartItemIds));
    return support
        .send(
            mvc,
            post("/api/v1/orders").with(req -> remoteAddr(req)),
            customer,
            "{\"cartItemIds\":[\"" + ids + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"" + method + "\"}")
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static MockHttpServletRequest remoteAddr(MockHttpServletRequest request) {
    request.setRemoteAddr("203.0.113.7");
    return request;
  }

  private static String orderIdOf(String body) {
    return JsonPath.read(body, "$.data.id");
  }

  private static String paymentUrlOf(String body) {
    return JsonPath.read(body, "$.data.paymentUrl");
  }

  private String lineOf(String body, User seller) {
    List<String> ids = JsonPath.read(body, "$.data.items[?(@.sellerId=='" + seller.getId() + "')].id");
    return ids.get(0);
  }

  private int stockOf(Product product) {
    return products.findById(product.getId()).orElseThrow().getStock();
  }
}
