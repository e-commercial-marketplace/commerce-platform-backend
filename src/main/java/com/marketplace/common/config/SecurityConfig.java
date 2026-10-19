package com.marketplace.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình biên bảo mật của ứng dụng trong Phase 0.
 *
 * <p>Các endpoint health/info, tài liệu OpenAPI và các endpoint public theo
 * {@code docs/API_CONTRACT.md §2.1} (auth, catalog đọc, review đọc, AI hỏi đáp) được gọi công khai.
 * Mọi endpoint còn lại phải được xác thực. Vì JWT filter của Phase 1 chưa được triển khai trong code
 * hiện tại, các endpoint public chưa có controller sẽ trả 404 cho đến khi Phase 1/2/10 bổ sung.
 *
 * <p>Matcher ở đây dùng URL thật mà client gọi, tức đã gồm prefix version {@code /api/v1} do
 * {@code WebConfig} thêm cho REST controller. Khi thêm endpoint public mới, khai báo controller
 * không kèm prefix nhưng matcher phải ghi đủ prefix, nếu không request sẽ bị chặn 401/403 dù
 * controller đã permitAll.
 *
 * <p>Ứng dụng dùng stateless session: request không dựa vào {@code HttpSession}. Khi Phase 1 thêm
 * JWT filter, client phải gửi token trong header {@code Authorization: Bearer <token>}. CSRF được
 * tắt vì API dự kiến dùng bearer token thay vì cookie-session; nếu sau này chuyển sang xác thực
 * bằng cookie thì phải đánh giá và bật lại CSRF.
 */
@Configuration
public class SecurityConfig {

  /**
   * Xây dựng chuỗi filter bảo vệ tất cả request HTTP.
   *
   * @param http builder Spring Security cung cấp để cấu hình filter chain
   * @return filter chain được Spring Security dùng cho mọi request
   * @throws Exception nếu Spring Security không xây dựng được chain
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable) // tắt CSRF cho REST API
        .sessionManagement(
            sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // không tạo session
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health", // health check cho Docker/K8s
                        "/actuator/info",
                        "/v3/api-docs/**", // Swagger OpenAPI
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    // Endpoint public theo API_CONTRACT §2.1 (controller bổ sung ở Phase 1/2/10).
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll() // đăng ký/đăng nhập không cần token
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll() // refresh dùng refresh token, không cần access token
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated() // logout yêu cầu access token của chính chủ
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products",
                        "/api/v1/products/*",
                        "/api/v1/products/*/reviews",
                        "/api/v1/categories")
                    .permitAll() // catalog và review đọc là public
                    .requestMatchers(HttpMethod.POST, "/api/v1/ai/ask")
                    .permitAll() // AI hỏi đáp public, không yêu cầu đăng nhập
                    .anyRequest()
                    .authenticated()); // còn lại: cần auth
    return http.build();
  }

  /**
   * Cung cấp encoder để Auth service lưu password hash.
   *
   * <p>Code đăng ký user phải gọi {@code passwordEncoder.encode(rawPassword)} trước khi lưu vào
   * cột tương ứng với {@code password_hash}; code đăng nhập phải dùng
   * {@code matches(rawPassword, storedHash)}. Không gọi {@code encode} rồi so sánh trực tiếp hai
   * chuỗi hash, vì BCrypt tạo salt mới cho mỗi lần encode.
   *
   * @return encoder BCrypt dùng chung trong Spring context
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
