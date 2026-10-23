package com.marketplace.common.exception;

import com.marketplace.common.response.ApiResponse;

/**
 * Exception gốc cho lỗi nghiệp vụ mà API có thể dự đoán và trả về có chủ đích.
 *
 * <p>Kế thừa {@link RuntimeException} để service có thể dừng luồng ngay tại rule vi phạm mà không
 * phải truyền exception qua mọi method trung gian. {@link GlobalExceptionHandler} bắt exception
 * này ở biên controller và chuyển thành {@link ApiResponse} với status của {@link ErrorCode}.
 *
 * <p>Dùng các hàm factory bên dưới thay vì tự {@code new}, để mỗi loại lỗi chỉ được tạo một cách. Không
 * dùng exception này để che lỗi lập trình hoặc lỗi hạ tầng; các lỗi đó phải đi qua fallback 500 để
 * client không nhận chi tiết nội bộ.
 */
public class BusinessException extends RuntimeException {

  /** Mã ổn định (kèm HTTP status) để mobile app xử lý theo loại lỗi thay vì phải parse message. */
  private final ErrorCode errorCode;

  /**
   * @param errorCode loại lỗi, quyết định mã và HTTP status trả cho client
   * @param message message dành cho client/log; không đưa secret hoặc stack trace vào đây
   */
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Lỗi 400 {@code VALIDATION_ERROR}: dữ liệu client gửi không hợp lệ về nghiệp vụ (ví dụ category
   * không tồn tại, tên trùng).
   */
  public static BusinessException validation(String message) {
    return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
  }

  /**
   * Lỗi 401 {@code AUTHENTICATION_REQUIRED}: thông tin xác thực sai hoặc không dùng được (mật khẩu
   * sai, refresh token hết hạn...).
   */
  public static BusinessException unauthorized(String message) {
    return new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, message);
  }

  /** Lỗi 400 {@code PRODUCT_INACTIVE}: product đã ẩn (ngừng bán), không thêm, sửa hay mua được. */
  public static BusinessException productInactive(String message) {
    return new BusinessException(ErrorCode.PRODUCT_INACTIVE, message);
  }

  /** Lỗi 409 {@code INSUFFICIENT_STOCK}: số lượng yêu cầu vượt tồn kho hiện tại. */
  public static BusinessException insufficientStock(String message) {
    return new BusinessException(ErrorCode.INSUFFICIENT_STOCK, message);
  }

  /** Lỗi 404 {@code CART_ITEM_NOT_FOUND}: không có dòng giỏ nào với id đó. */
  public static BusinessException cartItemNotFound(String message) {
    return new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND, message);
  }

  /**
   * Lỗi 404 {@code RESOURCE_NOT_FOUND}: không có resource với id đó, hoặc resource không thuộc người gọi
   * (trả cùng lỗi để không lộ id của người khác có tồn tại hay không).
   *
   * @param resource tên dễ đọc, ví dụ {@code Product}, {@code Order}, {@code User}
   */
  public static BusinessException notFound(String resource, Object id) {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + id);
  }

  /** Lỗi 403 {@code FORBIDDEN}: đã đăng nhập nhưng không được làm việc này (sai chủ sở hữu, chưa được duyệt). */
  public static BusinessException forbidden(String message) {
    return new BusinessException(ErrorCode.FORBIDDEN, message);
  }

  /**
   * Lỗi 409 {@code ORDER_NOT_PAYABLE}: order không thể thanh toán VNPAY nữa (đã trả, đã huỷ, là order
   * COD hoặc đã quá hạn 24 giờ).
   */
  public static BusinessException orderNotPayable(String message) {
    return new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, message);
  }

  /** Lỗi 409 {@code CANNOT_CANCEL_AFTER_PAYMENT}: order đã thanh toán trước, chưa có quy trình hoàn tiền. */
  public static BusinessException cannotCancelAfterPayment(String message) {
    return new BusinessException(ErrorCode.CANNOT_CANCEL_AFTER_PAYMENT, message);
  }

  /** Lỗi 409 {@code CANNOT_CANCEL_AFTER_SHIPMENT}: dòng hàng đã gán vào shipment; phải huỷ shipment thay vì huỷ dòng. */
  public static BusinessException cannotCancelAfterShipment(String message) {
    return new BusinessException(ErrorCode.CANNOT_CANCEL_AFTER_SHIPMENT, message);
  }

  /** @return loại lỗi; global handler lấy mã và HTTP status từ đây */
  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
