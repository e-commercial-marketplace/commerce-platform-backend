package com.marketplace.common.exception;

import com.marketplace.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Bắt exception tập trung cho mọi controller -> luôn trả về khung ApiResponse thống nhất.
@RestControllerAdvice
public class GlobalExceptionHandler {

  // Lỗi nghiệp vụ do service ném (BusinessException và subclass): giữ nguyên status + code.
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
  }

  // Lỗi validate @Valid trên @RequestBody: trả toàn bộ fieldErrors để client highlight từng field.
  // Message tổng hợp từ lỗi đầu tiên để tương thích client chỉ đọc message.
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError f : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(f.getField(), f.getDefaultMessage());
    }
    String message =
        fieldErrors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .findFirst()
            .orElse("Validation failed");
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("VALIDATION_ERROR", message, fieldErrors));
  }

  // Lỗi validate trên @RequestParam/@PathVariable (@Validated ở controller).
  // Key là tên field lá (ví dụ "email" thay vì "register.email") để khớp ví dụ fieldErrors trong contract.
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
      fieldErrors.putIfAbsent(leafNode(v.getPropertyPath()), v.getMessage());
    }
    // Message tổng hợp từ field lá (không dùng ex.getMessage() vì chứa full path).
    String message =
        fieldErrors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .findFirst()
            .orElse("Validation failed");
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("VALIDATION_ERROR", message, fieldErrors));
  }

  /** Lấy tên node lá của property path (ví dụ "register.email" -> "email"). */
  private static String leafNode(Path path) {
    String leaf = null;
    for (Path.Node node : path) {
      leaf = node.getName();
    }
    return leaf != null ? leaf : path.toString();
  }

  // Fallback cuối cùng: lỗi chưa lường trước -> 500, che message gốc để không lộ chi tiết nội bộ.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail("INTERNAL_ERROR", "Unexpected error"));
  }

  // Request tới đường dẫn không khớp handler nào (client gọi sai URL): Spring ném
  // NoResourceFoundException, phải trả 404 theo contract thay vì để fallback đổi thành 500.
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail("RESOURCE_NOT_FOUND", "Resource not found"));
  }
}
