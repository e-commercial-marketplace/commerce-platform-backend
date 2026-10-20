package com.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 403 cho request đã xác thực nhưng không được phép thao tác trên resource.
 *
 * <p>Ví dụ điển hình trong PLAN là seller A gọi sửa product thuộc seller B. Service phải kiểm tra
 * ownership sau khi xác định principal; không được tin {@code sellerId} do React Native gửi lên.
 *
 * <p>Phân biệt với 401: 401 là chưa có credential hợp lệ, còn exception này là đã đăng nhập nhưng
 * bị từ chối quyền trên resource cụ thể.
 */
public class ForbiddenException extends BusinessException {

  /**
   * @param message mô tả ngắn lý do bị từ chối, không chứa thông tin nhạy cảm của resource khác
   */
  public ForbiddenException(String message) {
    super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
  }
}
