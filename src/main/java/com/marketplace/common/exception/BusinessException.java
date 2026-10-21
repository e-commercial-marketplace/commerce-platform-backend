package com.marketplace.common.exception;

import com.marketplace.common.response.ApiResponse;
import org.springframework.http.HttpStatus;

/**
 * Exception gốc cho lỗi nghiệp vụ mà API có thể dự đoán và trả về có chủ đích.
 *
 * <p>Kế thừa {@link RuntimeException} để service có thể dừng luồng ngay tại rule vi phạm mà không
 * phải truyền exception qua mọi method trung gian. {@link GlobalExceptionHandler} bắt exception
 * này ở biên controller và chuyển thành {@link ApiResponse} với HTTP status tương ứng.
 *
 * <p>Dùng exception này cho các rule như voucher hết hạn, seller không sở hữu product hoặc resource
 * không tồn tại. Không dùng nó để che lỗi lập trình hoặc lỗi hạ tầng bất ngờ; các lỗi đó phải đi qua
 * fallback 500 để client không nhận chi tiết nội bộ.
 *
 * <p>Ví dụ: {@code throw new BusinessException("VOUCHER_EXPIRED", "Voucher has expired",
 * HttpStatus.BAD_REQUEST);}
 */
public class BusinessException extends RuntimeException {

  /** Mã ổn định để mobile app xử lý theo loại lỗi thay vì phải parse message. */
  private final String code;
  /** HTTP status mà controller boundary sẽ trả về cho client. */
  private final HttpStatus status;

  /**
   * Tạo lỗi nghiệp vụ với contract đầy đủ cho API.
   *
   * @param code mã lỗi ổn định, nên viết uppercase và dùng lại giữa các request cùng loại
   * @param message message dành cho client/log; không đưa secret hoặc stack trace vào đây
   * @param status HTTP status phù hợp với rule, ví dụ 400, 403 hoặc 404
   */
  public BusinessException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  /**
   * Lỗi 400 {@code VALIDATION_ERROR}: dữ liệu client gửi không hợp lệ về nghiệp vụ (ví dụ category
   * không tồn tại, tên trùng). Dùng hàm này thay vì tự ghi mã lỗi và status ở từng nơi.
   */
  public static BusinessException validation(String message) {
    return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
  }

  /**
   * Lỗi 401 {@code AUTHENTICATION_REQUIRED}: thông tin xác thực sai hoặc không dùng được (mật khẩu
   * sai, refresh token hết hạn...).
   */
  public static BusinessException unauthorized(String message) {
    return new BusinessException("AUTHENTICATION_REQUIRED", message, HttpStatus.UNAUTHORIZED);
  }

  /** Lỗi 400 {@code PRODUCT_INACTIVE}: product đã ẩn (ngừng bán), không thêm hay sửa trong giỏ được. */
  public static BusinessException productInactive(String message) {
    return new BusinessException("PRODUCT_INACTIVE", message, HttpStatus.BAD_REQUEST);
  }

  /** Lỗi 409 {@code INSUFFICIENT_STOCK}: số lượng yêu cầu vượt tồn kho hiện tại. */
  public static BusinessException insufficientStock(String message) {
    return new BusinessException("INSUFFICIENT_STOCK", message, HttpStatus.CONFLICT);
  }

  /** Lỗi 404 {@code CART_ITEM_NOT_FOUND}: không có dòng giỏ nào với id đó. */
  public static BusinessException cartItemNotFound(String message) {
    return new BusinessException("CART_ITEM_NOT_FOUND", message, HttpStatus.NOT_FOUND);
  }

  /**
   * Lỗi 404 {@code RESOURCE_NOT_FOUND}: không có resource với id đó, hoặc resource không thuộc người gọi
   * (trả cùng lỗi để không lộ id của người khác có tồn tại hay không).
   *
   * @param resource tên dễ đọc, ví dụ {@code Product}, {@code Order}, {@code User}
   */
  public static BusinessException notFound(String resource, Object id) {
    return new BusinessException("RESOURCE_NOT_FOUND", resource + " not found: " + id, HttpStatus.NOT_FOUND);
  }

  /** Lỗi 403 {@code FORBIDDEN}: đã đăng nhập nhưng không được làm việc này (sai chủ sở hữu, chưa được duyệt). */
  public static BusinessException forbidden(String message) {
    return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
  }

  /** @return mã lỗi để {@code ApiResponse.error.code} trả về cho client */
  public String getCode() {
    return code;
  }

  /** @return HTTP status mà global handler dùng để tạo {@code ResponseEntity} */
  public HttpStatus getStatus() {
    return status;
  }
}
