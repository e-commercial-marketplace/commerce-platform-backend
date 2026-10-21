package com.marketplace.config;

import com.marketplace.security.JwtAuthenticationFilter;
import com.marketplace.security.RestAccessDeniedHandler;
import com.marketplace.security.RestAuthenticationEntryPoint;
import com.marketplace.service.JwtService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cấu hình biên bảo mật của ứng dụng.
 *
 * <p>Các endpoint health/info, tài liệu OpenAPI và các endpoint public theo
 * {@code docs/API_CONTRACT.md §2.1} (auth, catalog đọc, review đọc, AI hỏi đáp) được gọi công khai.
 * Mọi endpoint còn lại phải được xác thực; {@code /api/v1/admin/**} chỉ dành cho ADMIN. Endpoint public
 * chưa có controller (catalog, review, AI) trả 404 cho đến khi Phase 2/8/10 bổ sung.
 *
 * <p>Matcher ở đây dùng URL thật mà client gọi, tức đã gồm prefix version {@code /api/v1} do
 * {@code WebConfig} thêm cho REST controller. Khi thêm endpoint public mới, khai báo controller
 * không kèm prefix nhưng matcher phải ghi đủ prefix, nếu không request sẽ bị chặn 401/403 dù
 * controller đã permitAll.
 *
 * <p>Ứng dụng dùng stateless session: request không dựa vào {@code HttpSession}. Client gửi access
 * token trong header {@code Authorization: Bearer <token>}; {@link JwtAuthenticationFilter} dựng
 * principal. Lỗi 401/403 ở tầng filter được trả dạng {@code ApiResponse} qua entry point/handler
 * riêng; {@code @EnableMethodSecurity} bật {@code @PreAuthorize} cho kiểm tra role ở controller. CSRF được
 * tắt vì API dự kiến dùng bearer token thay vì cookie-session; nếu sau này chuyển sang xác thực
 * bằng cookie thì phải đánh giá và bật lại CSRF.
 */
@Configuration // đánh dấu đây là class cấu hình: Spring đọc các @Bean bên trong khi khởi động
@EnableMethodSecurity // bật @PreAuthorize: cho phép kiểm tra quyền ngay trên method của controller
@RequiredArgsConstructor // Lombok tự tạo constructor nhận 3 field final bên dưới
public class SecurityConfig {

  // Dùng để kiểm tra token, được đưa vào JwtAuthenticationFilter.
  private final JwtService jwtService;
  // Quyết định nội dung lỗi 401 (chưa đăng nhập).
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  // Quyết định nội dung lỗi 403 (đã đăng nhập nhưng sai quyền).
  private final RestAccessDeniedHandler accessDeniedHandler;

  /**
   * Xây dựng chuỗi filter bảo vệ tất cả request HTTP.
   *
   * @param http builder Spring Security cung cấp để cấu hình filter chain
   * @return filter chain được Spring Security dùng cho mọi request
   * @throws Exception nếu Spring Security không xây dựng được chain
   */
  @Bean // kết quả của method này là chuỗi filter bảo vệ mọi request, Spring giữ nó làm bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // "http" là công cụ dựng chuỗi; mỗi dòng bên dưới thêm một quy tắc vào chuỗi.
    http.csrf(AbstractHttpConfigurer::disable) // Bước 1: tắt CSRF, vì API dùng bearer token chứ không dùng cookie
        // Bước 2: bật CORS, dùng bean corsConfigurationSource ở dưới (mặc định không cho origin nào)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            // Bước 3: STATELESS = server không nhớ ai qua session, mỗi request phải tự mang token
            sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            // Bước 4: khi bị chặn thì trả lỗi gì
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint) // chưa đăng nhập -> 401 dạng ApiResponse
                    .accessDeniedHandler(accessDeniedHandler)) // đăng nhập rồi nhưng sai quyền -> 403 dạng ApiResponse
        // Bước 5: gắn filter JWT của mình vào chuỗi, đứng ngay trước filter đăng nhập mặc định của Spring.
        // Dùng "new" thay vì @Component để filter không bị đăng ký hai lần.
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        // Bước 6: luật URL. Spring đọc từ TRÊN XUỐNG DƯỚI và dùng luật ĐẦU TIÊN khớp,
        // nên luật chung chung "anyRequest()" bắt buộc phải nằm cuối cùng.
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health", // health check cho Docker/K8s
                        "/actuator/info",
                        "/v3/api-docs/**", // Swagger OpenAPI
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll() // permitAll = ai cũng vào được, không cần token
                    // Endpoint public theo API_CONTRACT §2.1 (controller bổ sung ở Phase 1/2/10).
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll() // đăng ký/đăng nhập không cần token (chưa có token thì không thể có)
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset")
                    .permitAll() // người quên mật khẩu thì không có token; mã trong email thay cho token
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll() // refresh dùng refresh token, không cần access token (access đã hết hạn)
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated() // authenticated = phải đăng nhập (có token hợp lệ); logout cần access token của chính chủ
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products",
                        "/api/v1/products/*", // dấu * khớp đúng một đoạn, ví dụ /products/123
                        "/api/v1/products/*/reviews",
                        "/api/v1/categories",
                        "/api/v1/sellers/*") // trang shop của seller
                    .permitAll() // catalog, review và trang shop đọc là public (chỉ method GET)
                    .requestMatchers(HttpMethod.POST, "/api/v1/ai/ask")
                    .permitAll() // AI hỏi đáp public, không yêu cầu đăng nhập
                    .requestMatchers("/api/v1/orders/**")
                    .hasRole("CUSTOMER") // checkout và order chỉ dành cho customer: seller, admin -> 403
                    .requestMatchers("/api/v1/cart/**")
                    .hasRole("CUSTOMER") // giỏ hàng chỉ dành cho customer: seller, admin -> 403
                    .requestMatchers("/api/v1/seller/**")
                    .hasRole("SELLER") // khu vực seller: sai role (customer, admin) -> 403. Trạng thái APPROVED kiểm tra trong service
                    .requestMatchers("/api/v1/admin/**") // ** khớp mọi đường dẫn con
                    .hasRole("ADMIN") // phải đăng nhập VÀ có quyền ROLE_ADMIN, sai quyền -> 403
                    .anyRequest() // mọi request còn lại...
                    .authenticated()); // ...đều phải đăng nhập, chưa đăng nhập -> 401
    return http.build(); // dựng xong chuỗi và trả về cho Spring dùng
  }

  /**
   * Cấu hình CORS đọc từ {@code app.cors.allowed-origin-patterns} (biến {@code
   * APP_CORS_ALLOWED_ORIGIN_PATTERNS}, danh sách pattern phân cách bằng dấu phẩy).
   *
   * <p>Mặc định rỗng nghĩa là không origin nào được phép, giữ nguyên hành vi trước đây. Chỉ đặt giá
   * trị này khi cần gọi API từ trình duyệt, ví dụ {@code *} để thử bằng trang HTML mở trực tiếp từ
   * file lúc phát triển; production nên liệt kê đích danh origin của web app. Không bật {@code
   * allowCredentials} vì API dùng bearer token, không dùng cookie.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origin-patterns:}") List<String> allowedOriginPatterns) {
    // "source" là bảng quy tắc CORS theo từng đường dẫn; ban đầu để trống nghĩa là chặn mọi origin lạ.
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // Chỉ khi .env có đặt ít nhất một origin (không rỗng) thì mới thêm quy tắc cho phép.
    if (allowedOriginPatterns.stream().anyMatch(p -> !p.isBlank())) {
      CorsConfiguration config = new CorsConfiguration();
      // Những origin được phép gọi API (cắt khoảng trắng thừa), ví dụ "*" hoặc "http://localhost:5173".
      config.setAllowedOriginPatterns(allowedOriginPatterns.stream().map(String::trim).toList());
      // Những method HTTP được phép gọi từ trình duyệt.
      config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
      // Những header client được gửi lên: Authorization chứa token, Content-Type cho body JSON.
      config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
      // Trình duyệt được nhớ kết quả kiểm tra CORS (preflight) trong 3600 giây, đỡ phải hỏi lại.
      config.setMaxAge(3600L);
      source.registerCorsConfiguration("/**", config); // áp dụng cho mọi đường dẫn
    }
    return source;
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
  @Bean // Spring giữ một encoder dùng chung; nơi nào cần chỉ việc khai báo PasswordEncoder là được đưa vào
  public PasswordEncoder passwordEncoder() {
    // BCrypt: băm một chiều và tự thêm "muối" ngẫu nhiên, nên cùng một mật khẩu cho ra hash khác nhau mỗi lần.
    return new BCryptPasswordEncoder();
  }
}
