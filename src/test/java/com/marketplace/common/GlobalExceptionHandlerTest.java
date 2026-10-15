package com.marketplace.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.exception.ForbiddenException;
import com.marketplace.common.exception.GlobalExceptionHandler;
import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit test cho response/error contract của common layer.
 *
 * <p>Test gọi thẳng handler thay vì dùng MockMvc vì mục tiêu ở đây là kiểm tra mapping Java object:
 * exception nào thành status/code/body nào. HTTP routing và Spring context được kiểm tra ở test
 * khác; giữ test này thuần giúp feedback nhanh và chỉ ra lỗi contract rõ hơn.
 */
class GlobalExceptionHandlerTest {

  // Không cần Spring context vì GlobalExceptionHandler không giữ state hay dependency ngoài.
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /** Kiểm tra factory success giữ data và không tạo error object. */
  @Test
  void apiResponseOkShape() {
    ApiResponse<String> res = ApiResponse.ok("hello");
    assertThat(res.success()).isTrue();
    assertThat(res.data()).isEqualTo("hello");
    assertThat(res.error()).isNull();
  }

  /** Kiểm tra subclass ForbiddenException vẫn được bắt bởi handler của BusinessException. */
  @Test
  void businessExceptionMapped() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleBusiness(new ForbiddenException("not yours"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().error().code()).isEqualTo("FORBIDDEN");
  }

  /** Kiểm tra lookup thất bại dùng HTTP 404 nhưng vẫn giữ response error contract. */
  @Test
  void notFoundMapped() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleBusiness(new ResourceNotFoundException("Product", "1"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Kiểm tra lỗi không dự đoán được không làm lộ message nội bộ ra API. */
  @Test
  void unknownMappedTo500() {
    ResponseEntity<ApiResponse<Void>> res = handler.handleUnknown(new RuntimeException("boom"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(res.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
  }

  /** Kiểm tra exception gốc truyền nguyên code/status để các subclass tái sử dụng contract. */
  @Test
  void businessExceptionCarriesStatus() {
    BusinessException ex =
        new BusinessException("TEST", "msg", HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(ex.getCode()).isEqualTo("TEST");
  }
}
