package com.marketplace.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 404 khi resource được yêu cầu không tồn tại hoặc không thể truy cập theo contract hiện tại.
 *
 * <p>Dùng ở service sau thao tác lookup, ví dụ
 * {@code throw new ResourceNotFoundException("Product", productId)}. Cách dùng này giữ code lỗi
 * {@code RESOURCE_NOT_FOUND} (đúng contract §2) nhất quán và giúp client phân biệt 404 với lỗi
 * validation hoặc 500.
 */
public class ResourceNotFoundException extends BusinessException {

  /**
   * @param resource tên domain dễ đọc, ví dụ {@code Product}, {@code Order} hoặc {@code User}
   * @param id định danh dùng trong lookup; không dùng dữ liệu bí mật làm message
   */
  public ResourceNotFoundException(String resource, Object id) {
    super("RESOURCE_NOT_FOUND",resource + " not found: " + id, HttpStatus.NOT_FOUND);
  }
}
