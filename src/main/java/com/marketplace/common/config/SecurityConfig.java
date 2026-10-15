package com.marketplace.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình biên bảo mật của ứng dụng trong Phase 0.
 *
 * <p>Các endpoint health/info và tài liệu OpenAPI được gọi công khai để Docker, người vận hành và
 * frontend có thể kiểm tra ứng dụng. Mọi endpoint khác phải được xác thực. Vì JWT filter của Phase
 * 1 chưa được triển khai trong code hiện tại, comment này mô tả contract mong muốn; không được hiểu
 * là backend đã có đầy đủ luồng register/login/refresh.
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
                    .permitAll() // các URL trên: public
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
