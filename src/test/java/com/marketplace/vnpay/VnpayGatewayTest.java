package com.marketplace.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.config.VnpayProperties;
import com.marketplace.entity.Order;
import com.marketplace.entity.Payment;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

/** Ký và kiểm chữ ký VNPAY không cần Spring: chỉ cần khoá bí mật. */
class VnpayGatewayTest {

  private final VnpayGateway gateway =
      new VnpayGateway(
          new VnpayProperties(
              "TESTCODE", "unit-test-secret", "https://pay.example", "https://back", "app://r", 15));

  @Test
  void aResultSignedWithTheSecretVerifies() {
    Map<String, String> params = signed(sample());
    assertThat(gateway.verify(params)).isTrue();
  }

  @Test
  void changingAnyFieldOrTheSignatureBreaksVerification() {
    Map<String, String> tampered = signed(sample());
    tampered.put("vnp_Amount", "100"); // đổi số tiền sau khi ký
    assertThat(gateway.verify(tampered)).isFalse();

    Map<String, String> wrongHash = signed(sample());
    wrongHash.put(VnpayGateway.SECURE_HASH, "00" + wrongHash.get(VnpayGateway.SECURE_HASH).substring(2));
    assertThat(gateway.verify(wrongHash)).isFalse();

    Map<String, String> noHash = sample();
    assertThat(gateway.verify(noHash)).isFalse();
  }

  @Test
  void theHashTypeFieldAndUppercaseHashAreAccepted() {
    Map<String, String> params = signed(sample());
    params.put("vnp_SecureHashType", "HmacSHA512"); // VNPAY có thể gửi kèm, không tính vào chữ ký
    params.put(VnpayGateway.SECURE_HASH, params.get(VnpayGateway.SECURE_HASH).toUpperCase());
    assertThat(gateway.verify(params)).isTrue();
  }

  @Test
  void vnpayAmountIsTimesOneHundred() {
    assertThat(VnpayGateway.toVnpayAmount(new BigDecimal("34990000.00"))).isEqualTo(3_499_000_000L);
    assertThat(VnpayGateway.toVnpayAmount(new BigDecimal("59.50"))).isEqualTo(5950L);
  }

  @Test
  void theLinkNeverOutlivesTheOrderDeadline() {
    Instant now = Instant.parse("2026-10-22T10:00:00Z"); // 17:00 giờ Việt Nam
    Order order = Order.place(null, "A", now);
    Payment payment = Payment.pendingVnpay(order, "txn1");

    // Còn xa hạn: link sống đủ 15 phút.
    String normal = gateway.buildPaymentUrl(payment, "1.2.3.4", now, now.plus(Duration.ofHours(1)));
    assertThat(param(normal, "vnp_ExpireDate")).isEqualTo("20261022171500");
    // Hạn chỉ còn 5 phút: link hết hạn đúng lúc hết hạn của order, không sống lâu hơn.
    String capped = gateway.buildPaymentUrl(payment, "1.2.3.4", now, now.plus(Duration.ofMinutes(5)));
    assertThat(param(capped, "vnp_ExpireDate")).isEqualTo("20261022170500");
  }

  @Test
  void anEmptySecretIsAConfigurationError() {
    VnpayGateway unconfigured =
        new VnpayGateway(new VnpayProperties("T", "", "https://p", "https://b", "app://r", 15));
    assertThatThrownBy(() -> unconfigured.sign("a=1")).isInstanceOf(IllegalStateException.class);
  }

  private static String param(String url, String name) {
    return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
  }

  private static Map<String, String> sample() {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Amount", "10000000");
    params.put("vnp_OrderInfo", "Thanh toan don hang 1");
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TxnRef", "abc123");
    return params;
  }

  private Map<String, String> signed(Map<String, String> params) {
    Map<String, String> copy = new TreeMap<>(params);
    copy.put(VnpayGateway.SECURE_HASH, gateway.sign(VnpayGateway.canonicalQuery(new TreeMap<>(params))));
    return copy;
  }
}
