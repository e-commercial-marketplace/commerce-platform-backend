package com.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.common.exception.ErrorCode;
import com.marketplace.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Trả lỗi 401 và 403 của tầng filter security theo khung {@link ApiResponse}.
 *
 * <p>Lỗi ở tầng filter xảy ra trước khi request tới controller, nên {@code GlobalExceptionHandler}
 * không bắt được; thiếu class này Spring sẽ trả trang lỗi mặc định, sai contract. Hai tình huống chỉ
 * khác mã lỗi, nên dùng chung một class và một hàm ghi.
 */
@Component // để Spring tạo sẵn đối tượng này và SecurityConfig đưa vào dùng
@RequiredArgsConstructor // Lombok tạo constructor nhận objectMapper
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  // Công cụ của Spring để đổi đối tượng Java thành chuỗi JSON.
  private final ObjectMapper objectMapper;

  // CHƯA ĐĂNG NHẬP (không có token, token sai hoặc hết hạn) mà gọi endpoint cần đăng nhập -> 401.
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    write(response, ErrorCode.AUTHENTICATION_REQUIRED);
  }

  // ĐÃ ĐĂNG NHẬP nhưng sai role theo luật URL (ví dụ customer vào /admin) -> 403.
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(response, ErrorCode.FORBIDDEN);
  }

  /** Ghi lỗi: status theo mã, nội dung JSON theo khung ApiResponse với câu mặc định của mã. */
  private void write(HttpServletResponse response, ErrorCode code) throws IOException {
    response.setStatus(code.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiResponse.fail(code.name(), code.getDefaultMessage()));
  }
}
