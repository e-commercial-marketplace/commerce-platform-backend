package com.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception gốc cho lỗi nghiệp vụ mà API có thể dự đoán và trả về có chủ đích.
 *
 * <p>Kế thừa {@link RuntimeException} để service có thể dừng luồng ngay tại rule vi phạm mà không
 * phải truyền exception qua mọi method trung gian. {@link GlobalExceptionHandler} bắt exception
 * này ở biên controller và chuyển thành {@link com.marketplace.common.response.ApiResponse#fail}
 * với HTTP status tương ứng.
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

  /** @return mã lỗi để {@code ApiResponse.error.code} trả về cho client */
  public String getCode() {
    return code;
  }

  /** @return HTTP status mà global handler dùng để tạo {@code ResponseEntity} */
  public HttpStatus getStatus() {
    return status;
  }
}
