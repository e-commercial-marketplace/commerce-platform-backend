package com.marketplace.common.exception;

import com.marketplace.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Điểm chuyển đổi exception từ application layer thành HTTP response thống nhất.
 *
 * <p>Controller/service không cần tự tạo response lỗi cho từng nhánh. Khi exception đi lên boundary
 * này, client luôn nhận {@code ApiResponse} theo các nhóm sau:
 *
 * <ul>
 *   <li>{@link BusinessException}: giữ nguyên mã lỗi và HTTP status đã chọn.
 *   <li>{@link MethodArgumentNotValidException}: 400 với lỗi field đầu tiên của {@code @Valid}.
 *   <li>{@link ConstraintViolationException}: 400 cho request parameter/path validation.
 *   <li>{@link Exception}: 500 với message chung, không làm lộ chi tiết nội bộ.
 * </ul>
 *
 * <p>Handler này chỉ định dạng lỗi. Quyền truy cập, ownership, transaction và business rule vẫn
 * phải được kiểm tra trong service/controller tương ứng.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Map lỗi nghiệp vụ do service chủ động ném.
   *
   * @param ex lỗi đã chứa code, message và status phù hợp
   * @return response có cùng HTTP status và body {@code success=false}
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
  }

  /**
   * Map lỗi validation của {@code @RequestBody @Valid}.
   *
   * <p>Hiện tại chỉ trả lỗi field đầu tiên để giữ contract ngắn gọn; client không nên giả định mọi
   * lỗi trong request đều được liệt kê ở response này.
   *
   * @param ex lỗi binding do Spring MVC tạo
   * @return HTTP 400 với code {@code VALIDATION_ERROR}
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
  }

  /**
   * Map lỗi validation trên {@code @RequestParam} hoặc {@code @PathVariable}.
   *
   * @param ex lỗi constraint do Jakarta Validation tạo
   * @return HTTP 400 với code {@code VALIDATION_ERROR}
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", ex.getMessage()));
  }

  /**
   * Fallback cho lỗi chưa có handler chuyên biệt.
   *
   * <p>Không trả {@code ex.getMessage()} cho mobile app vì message có thể chứa SQL, đường dẫn file
   * hoặc chi tiết hệ thống. Khi triển khai production nên ghi exception vào logging/observability
   * nội bộ với correlation id, nhưng vẫn giữ response public ở mức tổng quát.
   *
   * @param ex lỗi chưa được phân loại
   * @return HTTP 500 với code {@code INTERNAL_ERROR} và message an toàn
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail("INTERNAL_ERROR", "Unexpected error"));
  }
}
