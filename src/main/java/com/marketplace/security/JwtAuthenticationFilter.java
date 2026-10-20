package com.marketplace.security;

import com.marketplace.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc {@code Authorization: Bearer <token>} và đặt {@link AuthUser} vào SecurityContext.
 *
 * <p>Cố ý không phải {@code @Component}: Spring Boot sẽ tự đăng ký mọi bean Filter làm servlet
 * filter, khiến filter chạy hai lần. {@code SecurityConfig} tự khởi tạo và gắn vào security chain.
 *
 * <p>Token sai hoặc hết hạn không ném lỗi ở đây; request đi tiếp như anonymous và entry point trả 401
 * nếu endpoint yêu cầu xác thực. Nhờ vậy endpoint public vẫn gọi được khi client gửi token cũ.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      jwtService
          .parseAccessToken(header.substring(BEARER_PREFIX.length()))
          .ifPresent(
              user ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(user, null, user.authorities())));
    }
    chain.doFilter(request, response);
  }
}
