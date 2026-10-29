package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.util.ClientIp;
import com.marketplace.config.VnpayProperties;
import com.marketplace.dto.request.VnpayCreateRequest;
import com.marketplace.dto.response.VnpayIpnResponse;
import com.marketplace.dto.response.VnpayPaymentResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.PaymentService;
import com.marketplace.service.PaymentService.VnpayResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thanh toán VNPAY theo features/payment/API_CONTRACT.md. URL thật là {@code /api/v1/payments/...}.
 *
 * <ul>
 *   <li>{@code POST /vnpay/create}: customer thanh toán lại (luật {@code /payments/**} chỉ CUSTOMER).
 *   <li>{@code GET /vnpay/ipn}: VNPAY gọi server-to-server; công khai, xác thực bằng chữ ký VNPAY.
 *   <li>{@code GET /vnpay/return}: trình duyệt quay về sau trang VNPAY; công khai, cùng cách xử lý IPN.
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
public class PaymentController {

  // Trạng thái gửi về app khi kết quả không hợp lệ (sai chữ ký, mã lạ, sai số tiền) hoặc lỗi.
  private static final String INVALID_STATUS = "INVALID";

  private final PaymentService paymentService;
  private final VnpayProperties vnpayProperties;

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<VnpayPaymentResponse>> create(
      @AuthenticationPrincipal AuthUser me,
      @Valid @RequestBody VnpayCreateRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok(
        ApiResponse.ok(paymentService.retryVnpayPayment(me.id(), request.orderId(), ClientIp.of(http))));
  }

  /**
   * VNPAY chỉ đọc JSON {@code RspCode}/{@code Message}, nên endpoint này không bọc ApiResponse và luôn trả
   * 200; lỗi bất ngờ trả mã 99 để VNPAY biết mà gửi lại sau.
   */
  @GetMapping("/ipn")
  public ResponseEntity<VnpayIpnResponse> ipn(@RequestParam Map<String, String> params) {
    try {
      return ResponseEntity.ok(paymentService.handleVnpayCallback(params).response());
    } catch (RuntimeException e) {
      log.error("VNPAY IPN failed", e);
      return ResponseEntity.ok(VnpayIpnResponse.UNKNOWN_ERROR);
    }
  }

  /**
   * Xử lý kết quả giống IPN (để chạy local, khi VNPAY không gọi tới được IPN, vẫn ghi nhận được), rồi
   * chuyển trình duyệt về app với orderId và trạng thái. App phải đọc trạng thái thật qua
   * {@code GET /orders/{id}}; query này chỉ để chọn màn hình hiển thị.
   */
  @GetMapping("/return")
  public ResponseEntity<Void> returnFromVnpay(@RequestParam Map<String, String> params) {
    UriComponentsBuilder target = UriComponentsBuilder.fromUriString(vnpayProperties.appRedirectUrl());
    try {
      VnpayResult result = paymentService.handleVnpayCallback(params);
      if (result.orderId() != null) {
        target.queryParam("orderId", result.orderId());
      }
      target.queryParam(
          "status",
          result.paymentStatus() == null ? INVALID_STATUS : result.paymentStatus().name());
    } catch (RuntimeException e) {
      log.error("VNPAY return failed", e);
      target.queryParam("status", INVALID_STATUS);
    }
    URI location = target.build().toUri();
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }
}
