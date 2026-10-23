package com.marketplace.vnpay;

import com.marketplace.config.VnpayProperties;
import com.marketplace.entity.Payment;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nói chuyện với VNPAY theo chuẩn thanh toán 2.1.0: dựng link thanh toán có chữ ký và kiểm chữ ký kết
 * quả VNPAY gửi về (IPN và Return URL).
 *
 * <p>Cách ký của VNPAY: lấy các tham số {@code vnp_*}, sắp theo tên (alphabet), URL-encode giá trị, nối
 * thành chuỗi {@code a=1&b=2...}, rồi băm HMAC-SHA512 bằng khoá bí mật. Ai không có khoá thì không tạo
 * được chữ ký đúng, nên backend tin được kết quả nào có chữ ký khớp.
 */
@Component
@RequiredArgsConstructor
public class VnpayGateway {

  /** Tên tham số chữ ký; không tính vào chuỗi được ký. */
  public static final String SECURE_HASH = "vnp_SecureHash";

  private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
  private static final String HMAC_SHA512 = "HmacSHA512";
  // VNPAY đọc ngày giờ theo giờ Việt Nam, định dạng yyyyMMddHHmmss.
  private static final DateTimeFormatter VNPAY_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  private final VnpayProperties properties;

  /**
   * Link thanh toán cho một lần thử VNPAY.
   *
   * @param payment lần thử đang chờ; {@code transactionId} của nó là mã tham chiếu gửi đi (vnp_TxnRef)
   * @param clientIp IP của khách (VNPAY bắt buộc có vnp_IpAddr)
   * @param now thời điểm tạo link, dùng cho ngày tạo và ngày hết hạn
   * @param deadline hạn thanh toán của order; link không bao giờ sống quá mốc này, để khách không thể
   *     trả tiền cho order đã bị huỷ vì quá hạn
   */
  public String buildPaymentUrl(Payment payment, String clientIp, Instant now, Instant deadline) {
    // TreeMap giữ key theo thứ tự alphabet, đúng thứ tự VNPAY yêu cầu khi ký.
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Version", "2.1.0");
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", properties.tmnCode());
    params.put("vnp_Amount", String.valueOf(toVnpayAmount(payment.getAmount())));
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_TxnRef", payment.getTransactionId());
    // Không dấu, không ký tự đặc biệt để tránh lỗi mã hoá phía VNPAY.
    params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrder().getId());
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    params.put("vnp_ReturnUrl", properties.returnUrl());
    params.put("vnp_IpAddr", clientIp);
    params.put("vnp_CreateDate", VNPAY_TIME.format(now));
    Instant linkExpiry = now.plusSeconds(60L * properties.expireMinutes());
    params.put("vnp_ExpireDate", VNPAY_TIME.format(linkExpiry.isBefore(deadline) ? linkExpiry : deadline));

    String query = canonicalQuery(params);
    return properties.payUrl() + "?" + query + "&" + SECURE_HASH + "=" + sign(query);
  }

  /**
   * Chữ ký trong {@code params} có đúng do VNPAY tạo không. Ký lại các tham số {@code vnp_*} (trừ hai
   * tham số chữ ký) rồi so với {@code vnp_SecureHash}.
   */
  public boolean verify(Map<String, String> params) {
    String received = params.get(SECURE_HASH);
    if (received == null) {
      return false;
    }
    Map<String, String> signed = new TreeMap<>();
    params.forEach(
        (key, value) -> {
          if (key.startsWith("vnp_")
              && !key.equals(SECURE_HASH)
              && !key.equals(SECURE_HASH_TYPE)
              && value != null
              && !value.isEmpty()) {
            signed.put(key, value);
          }
        });
    String expected = sign(canonicalQuery(signed));
    // isEqual so hết chuỗi với thời gian cố định: không lộ "khớp được bao nhiêu ký tự" qua thời gian chạy.
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
  }

  /** VNPAY nhận số tiền nhân 100, không có phần thập phân (100000.00 VND -> 10000000). */
  public static long toVnpayAmount(BigDecimal amount) {
    return amount.movePointRight(2).longValueExact();
  }

  /** Ký một chuỗi bằng HMAC-SHA512 với khoá bí mật; trả về hex chữ thường. Dùng cả khi test tự ký. */
  public String sign(String data) {
    String secret = properties.hashSecret();
    if (secret == null || secret.isBlank()) {
      // Chưa đặt APP_VNPAY_HASH_SECRET: báo rõ lỗi cấu hình thay vì để thư viện mã hoá ném lỗi khó hiểu.
      throw new IllegalStateException("VNPAY is not configured: APP_VNPAY_HASH_SECRET is empty");
    }
    try {
      Mac mac = Mac.getInstance(HMAC_SHA512);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Cannot sign VNPAY data", e); // lỗi cấu hình, không phải lỗi client
    }
  }

  /** {@code key=value&...} theo thứ tự key, giá trị URL-encode US-ASCII (giống code mẫu của VNPAY). */
  public static String canonicalQuery(Map<String, String> sortedParams) {
    return sortedParams.entrySet().stream()
        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
        .collect(Collectors.joining("&"));
  }
}
