package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Câu trả lời cho IPN theo đúng định dạng VNPAY yêu cầu ({@code {"RspCode":"00","Message":"..."}}),
 * không bọc ApiResponse. VNPAY đọc {@code RspCode} để biết backend đã ghi nhận kết quả hay chưa.
 */
public record VnpayIpnResponse(
    @JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {

  public static final VnpayIpnResponse CONFIRMED = new VnpayIpnResponse("00", "Confirm Success");
  public static final VnpayIpnResponse NOT_FOUND = new VnpayIpnResponse("01", "Order not found");
  public static final VnpayIpnResponse ALREADY_CONFIRMED =
      new VnpayIpnResponse("02", "Order already confirmed");
  public static final VnpayIpnResponse INVALID_AMOUNT = new VnpayIpnResponse("04", "Invalid amount");
  public static final VnpayIpnResponse INVALID_SIGNATURE =
      new VnpayIpnResponse("97", "Invalid signature");
  public static final VnpayIpnResponse UNKNOWN_ERROR = new VnpayIpnResponse("99", "Unknown error");
}
