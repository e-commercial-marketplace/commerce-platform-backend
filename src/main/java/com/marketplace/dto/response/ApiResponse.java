package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Bỏ các field null khỏi JSON để response gọn (ví dụ: success thì không có "error").
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Khung response chuẩn cho mọi API: { success, data, error, timestamp, traceId }.
 *
 * <p>Contract này khớp {@code docs/API_CONTRACT.md §2.3}: mọi response có body đều dùng envelope này,
 * {@code 204 No Content} không có body. {@code traceId} giúp đối chiếu lỗi client với log server.
 *
 * @param success {@code true} nếu xử lý thành công, {@code false} nếu lỗi
 * @param data dữ liệu trả về (chỉ có khi success = true)
 * @param error chi tiết lỗi (chỉ có khi success = false)
 * @param timestamp thời điểm tạo response (UTC)
 * @param traceId định danh request, sinh mới cho mỗi response
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp, String traceId) {

  /** Shortcut cho response thành công: success=true + traceId mới + timestamp hiện tại. */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, Instant.now(), newTraceId());
  }

  /** Shortcut cho response lỗi không có lỗi field chi tiết. */
  public static <T> ApiResponse<T> fail(String code, String message) {
    return fail(code, message, null);
  }

  /**
   * Shortcut cho response lỗi kèm lỗi validation từng field.
   *
   * @param fieldErrors map tên field -> thông điệp lỗi, null khi không phải lỗi validation
   */
  public static <T> ApiResponse<T> fail(
      String code, String message, Map<String, String> fieldErrors) {
    return new ApiResponse<>(
        false, null, new ApiError(code, message, fieldErrors), Instant.now(), newTraceId());
  }

  /**
   * Chi tiết lỗi: code để client switch-case, message để hiển thị/log, fieldErrors cho lỗi validation.
   */
  public record ApiError(String code, String message, Map<String, String> fieldErrors) {}

  /** Sinh traceId ngẫu nhiên (UUID) cho mỗi response để tương quan với log server. */
  // TODO (hardening): propagate X-Request-Id/MDC qua filter để trace end-to-end thay vì UUID mới.
  private static String newTraceId() {
    return UUID.randomUUID().toString();
  }
}
