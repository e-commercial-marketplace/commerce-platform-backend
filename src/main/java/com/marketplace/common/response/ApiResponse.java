package com.marketplace.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Khung response chuẩn cho các API của marketplace: {@code { success, data, error, timestamp }}.
 *
 * <p>Jackson bỏ các field {@code null}, nên response thành công thường có {@code success=true,
 * data=...} và response lỗi có {@code success=false, error=...}. Mobile app nên đọc
 * {@code success} trước, sau đó mới dùng {@code data} hoặc {@code error.code}; không nên suy đoán
 * thành công chỉ từ HTTP 200 vì response contract vẫn là nguồn sự thật ở application layer.
 *
 * <p>Dùng {@link #ok(Object)} trong controller khi xử lý thành công và {@link #fail(String,
 * String)} qua exception handler khi có lỗi dự đoán được. {@code timestamp} là UTC theo chuẩn
 * {@link Instant}, phù hợp để log và đồng bộ giữa mobile/backend.
 *
 * @param success {@code true} nếu xử lý thành công, {@code false} nếu lỗi
 * @param data dữ liệu trả về; thường chỉ có khi {@code success=true}
 * @param error chi tiết lỗi; thường chỉ có khi {@code success=false}
 * @param timestamp thời điểm tạo response theo UTC
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

  /**
   * Tạo response thành công.
   *
   * <p>Ví dụ: {@code return ResponseEntity.ok(ApiResponse.ok(productResponse));}. Với endpoint
   * không có body, dùng {@code ApiResponse.<Void>ok(null)}.
   *
   * @param data payload thành công, có thể là object/list/page response
   * @param <T> kiểu payload
   * @return response có {@code success=true}, không có error và timestamp hiện tại
   */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, Instant.now());
  }

  /**
   * Tạo response lỗi theo contract chung.
   *
   * <p>{@code code} phải ổn định để client xử lý bằng switch/map; {@code message} chỉ là thông tin
   * hiển thị/log và không được chứa secret hoặc stack trace.
   *
   * @param code mã lỗi ổn định, ví dụ {@code VALIDATION_ERROR} hoặc {@code NOT_FOUND}
   * @param message mô tả an toàn cho client
   * @param <T> kiểu payload, thường là {@code Void} khi lỗi
   * @return response có {@code success=false}, không có data và có error
   */
  public static <T> ApiResponse<T> fail(String code, String message) {
    return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
  }

  /** Chi tiết lỗi: code để client xử lý ổn định, message để hiển thị/log. */
  public record ApiError(String code, String message) {}
}
