package com.marketplace.common.exception;

import com.marketplace.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Bắt exception tập trung cho mọi controller -> luôn trả về khung ApiResponse thống nhất.
// Mã lỗi và HTTP status luôn lấy từ ErrorCode, không ghép tay ở từng hàm.
@RestControllerAdvice
public class GlobalExceptionHandler {

  // Lỗi nghiệp vụ do service ném: mã và status theo ErrorCode của exception, message do service đặt.
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    return error(ex.getErrorCode(), ex.getMessage(), null);
  }

  // Lỗi validate @Valid trên @RequestBody: trả toàn bộ fieldErrors để client highlight từng field.
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError f : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(f.getField(), f.getDefaultMessage());
    }
    return validationError(fieldErrors);
  }

  // Lỗi validate trên @RequestParam/@PathVariable (@Validated ở controller).
  // Key là tên field lá (ví dụ "email" thay vì "register.email") để khớp ví dụ fieldErrors trong contract.
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
      fieldErrors.putIfAbsent(leafNode(v.getPropertyPath()), v.getMessage());
    }
    return validationError(fieldErrors);
  }

  // Query param/path variable sai kiểu (ví dụ categoryId=abc, page=x): lỗi của client -> 400.
  // Chỉ trả tên tham số, không trả giá trị hay tên kiểu nội bộ.
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return error(
        ErrorCode.VALIDATION_ERROR,
        ex.getName() + ": invalid value",
        Map.of(ex.getName(), "Invalid value"));
  }

  // Body JSON hỏng hoặc giá trị enum không hợp lệ (ví dụ "role": "FOO"): lỗi của client -> 400,
  // không để rơi xuống fallback 500. Không trả message gốc vì nó lộ tên class/kiểu nội bộ.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
    return error(ErrorCode.VALIDATION_ERROR, "Malformed request body", null);
  }

  // @PreAuthorize từ chối ném AccessDeniedException bên trong controller, nên advice này bắt trước
  // Spring Security; thiếu handler thì fallback bên dưới biến nó thành 500.
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return error(ErrorCode.FORBIDDEN);
  }

  // Request tới đường dẫn không khớp handler nào (client gọi sai URL): Spring ném
  // NoResourceFoundException, phải trả 404 theo contract thay vì để fallback đổi thành 500.
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
    return error(ErrorCode.RESOURCE_NOT_FOUND);
  }

  // Fallback cuối cùng: lỗi chưa lường trước -> 500, che message gốc để không lộ chi tiết nội bộ.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
    return error(ErrorCode.INTERNAL_ERROR);
  }

  /** 400 VALIDATION_ERROR; message lấy từ lỗi đầu tiên cho client chỉ đọc message. */
  private static ResponseEntity<ApiResponse<Void>> validationError(Map<String, String> fieldErrors) {
    String message =
        fieldErrors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .findFirst()
            .orElse(ErrorCode.VALIDATION_ERROR.getDefaultMessage());
    return error(ErrorCode.VALIDATION_ERROR, message, fieldErrors);
  }

  /** Lỗi với câu mặc định của mã (dùng khi không được lộ message gốc). */
  private static ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
    return error(code, code.getDefaultMessage(), null);
  }

  /** Dựng response lỗi: status theo mã, body theo khung ApiResponse. */
  private static ResponseEntity<ApiResponse<Void>> error(
      ErrorCode code, String message, Map<String, String> fieldErrors) {
    return ResponseEntity.status(code.getStatus())
        .body(ApiResponse.fail(code.name(), message, fieldErrors));
  }

  private static String leafNode(Path path) {
    String leaf = null;
    for (Path.Node node : path) {
      leaf = node.getName();
    }
    return leaf != null ? leaf : path.toString();
  }
}
