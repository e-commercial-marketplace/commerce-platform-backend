package com.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Đã xác thực nhưng sai role theo URL matcher: trả 403 trong envelope ApiResponse. */
@Component // để Spring tạo sẵn đối tượng này và SecurityConfig đưa vào dùng
@RequiredArgsConstructor // Lombok tạo constructor nhận objectMapper
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  // Công cụ của Spring để đổi đối tượng Java thành chuỗi JSON.
  private final ObjectMapper objectMapper;

  // Spring gọi hàm này khi người dùng ĐÃ ĐĂNG NHẬP nhưng không đủ quyền (ví dụ customer vào /admin).
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    // Bước 1: mã trạng thái HTTP là 403 (khác 401: danh tính đã rõ, chỉ là không được phép).
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    // Bước 2: nội dung trả về là JSON.
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    // Bước 3: ghi lỗi theo khung ApiResponse vào response.
    objectMapper.writeValue(
        response.getOutputStream(), ApiResponse.fail("FORBIDDEN", "Access denied"));
  }
}
