package com.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi trả cho client trong {@code ApiResponse.error.code}, theo danh sách mã của
 * {@code docs/API_CONTRACT.md §2.3}. Mỗi mã đi kèm đúng một HTTP status và một câu mặc định, nên mã và
 * status không thể lệch nhau giữa các nơi trả lỗi (service, handler, filter security).
 *
 * <p>Chỉ thêm mã khi đã có code dùng tới; các mã của phase sau (voucher, payment...) thêm khi làm phase đó.
 */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  PRODUCT_INACTIVE(HttpStatus.BAD_REQUEST, "Product is no longer available"),
  INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Not enough stock"),
  CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found"),
  ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "Order is not payable"),
  CANNOT_CANCEL_AFTER_PAYMENT(HttpStatus.CONFLICT, "Order is already paid"),
  CANNOT_CANCEL_AFTER_SHIPMENT(HttpStatus.CONFLICT, "Order item is already in a shipment"),
  VOUCHER_EXPIRED(HttpStatus.CONFLICT, "Voucher is not valid at this time"),
  VOUCHER_EXHAUSTED(HttpStatus.CONFLICT, "Voucher has no use left"),
  REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "You can review a product only after a completed order"),
  REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already reviewed this product"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again in a minute"),
  UPLOAD_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Image upload is not configured"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  /** @return HTTP status trả về cùng mã này */
  public HttpStatus getStatus() {
    return status;
  }

  /** @return câu dùng khi không có message riêng (lỗi ở tầng filter, lỗi hạ tầng) */
  public String getDefaultMessage() {
    return defaultMessage;
  }
}
