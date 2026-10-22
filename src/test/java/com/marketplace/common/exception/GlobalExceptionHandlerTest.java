package com.marketplace.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketplace.common.response.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Test đơn vị cho khung lỗi chuẩn (khớp docs/API_CONTRACT.md §2.3: envelope + traceId + fieldErrors).
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void apiResponseOkShape() {
    ApiResponse<String> res = ApiResponse.ok("hello");
    assertThat(res.success()).isTrue();
    assertThat(res.data()).isEqualTo("hello");
    assertThat(res.error()).isNull();
    // Mỗi response thành công phải có traceId để đối chiếu với log server.
    assertThat(res.traceId()).isNotBlank();
    assertThat(res.timestamp()).isNotNull();
  }

  @Test
  void failCarriesFieldErrors() {
    // Lỗi validation phải giữ map field -> message để client highlight từng field.
    ApiResponse<Void> res =
        ApiResponse.fail("VALIDATION_ERROR", "quantity: Must be greater than zero",
            Map.of("quantity", "Must be greater than zero"));
    assertThat(res.success()).isFalse();
    assertThat(res.data()).isNull();
    assertThat(res.error().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(res.error().fieldErrors()).containsEntry("quantity", "Must be greater than zero");
    assertThat(res.traceId()).isNotBlank();
  }

  @Test
  void businessExceptionMapped() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleBusiness(BusinessException.forbidden("not yours"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().success()).isFalse();
    assertThat(res.getBody().error().code()).isEqualTo("FORBIDDEN");
    assertThat(res.getBody().traceId()).isNotBlank();
  }

  @Test
  void notFoundMapped() {
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleBusiness(BusinessException.notFound("Product", "1"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
  }

  @Test
  void unknownMappedTo500() {
    // Exception lạ phải che message gốc, trả 500 + code INTERNAL_ERROR (tránh lộ nội bộ).
    ResponseEntity<ApiResponse<Void>> res = handler.handleUnknown(new RuntimeException("boom"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(res.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void unmappedRouteMappedTo404() {
    // Đường dẫn không khớp handler nào phải trả 404 (không phải 500) theo contract: fallback
    // handleUnknown không được "nuốt" NoResourceFoundException của Spring.
    ResponseEntity<ApiResponse<Void>> res =
        handler.handleNoResourceFound(
            new NoResourceFoundException(HttpMethod.GET, "/api/v1/not-a-route"));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
    assertThat(res.getBody().error().message()).doesNotContain("/api/v1/not-a-route");
  }

  @Test
  void businessExceptionCarriesStatus() {
    // Mã và HTTP status luôn đi cùng nhau qua ErrorCode.
    BusinessException ex = BusinessException.insufficientStock("msg");
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(handler.handleBusiness(ex).getBody().error().code()).isEqualTo("INSUFFICIENT_STOCK");
  }

  // Serialize JSON thật để chắc envelope trên dây khớp contract (traceId, fieldErrors, NON_NULL).
  @Test
  void apiResponseSerializesContractEnvelope() throws Exception {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    String err =
        mapper.writeValueAsString(
            ApiResponse.fail(
                "VALIDATION_ERROR", "quantity: required", Map.of("quantity", "required")));
    assertThat(err).contains("\"success\":false");
    assertThat(err).contains("\"code\":\"VALIDATION_ERROR\"");
    assertThat(err).contains("\"fieldErrors\":{\"quantity\":\"required\"}");
    assertThat(err).contains("\"traceId\"");

    String ok = mapper.writeValueAsString(ApiResponse.ok("x"));
    assertThat(ok).contains("\"success\":true").contains("\"data\":\"x\"");
    assertThat(ok).doesNotContain("\"error\"");
  }
}
