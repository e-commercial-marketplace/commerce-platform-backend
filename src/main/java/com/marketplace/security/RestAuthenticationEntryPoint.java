package com.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Request chưa xác thực vào endpoint cần đăng nhập: trả 401 trong envelope ApiResponse. */
@Component // để Spring tạo sẵn đối tượng này và SecurityConfig đưa vào dùng
@RequiredArgsConstructor // Lombok tạo constructor nhận objectMapper
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  // Công cụ của Spring để đổi đối tượng Java thành chuỗi JSON.
  private final ObjectMapper objectMapper;

  // Spring gọi hàm này khi một request CHƯA ĐĂNG NHẬP đụng vào endpoint cần đăng nhập.
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    // Bước 1: đặt mã trạng thái HTTP là 401.
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    // Bước 2: báo cho client biết nội dung trả về là JSON.
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    // Bước 3: ghi nội dung lỗi (cùng khung ApiResponse như mọi lỗi khác) vào response.
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiResponse.fail("AUTHENTICATION_REQUIRED", "Authentication required"));
  }
}
