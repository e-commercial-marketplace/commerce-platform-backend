package com.marketplace.config;

import com.marketplace.enums.Role;
import com.marketplace.security.JwtAuthenticationFilter;
import com.marketplace.security.RestSecurityErrorHandler;
import com.marketplace.service.JwtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * <p>Luật URL được gom thành các hằng số ở đầu class: muốn mở hay khoá một endpoint thì chỉ thêm đường
 * dẫn vào đúng nhóm, không phải sửa chuỗi cấu hình. Nhóm công khai theo {@code docs/API_CONTRACT.md
 * §2.1}; mọi endpoint không nằm trong nhóm nào đều phải đăng nhập.
 *
 * <p>Đường dẫn ở đây là URL thật mà client gọi, tức đã gồm prefix {@code /api/v1} do {@code WebConfig}
 * thêm cho REST controller. Controller khai báo route không kèm prefix, nhưng hằng số ở đây phải ghi đủ
 * prefix (dùng {@link #API}), nếu không request sẽ bị chặn 401/403 dù đã khai báo công khai.
 *
 * <p>Ứng dụng dùng stateless session: client gửi access token trong header {@code Authorization: Bearer
 * <token>}, {@link JwtAuthenticationFilter} dựng principal. Lỗi 401/403 ở tầng filter trả dạng {@code
 * ApiResponse} qua entry point/handler riêng. CSRF tắt vì API dùng bearer token chứ không dùng cookie;
 * nếu sau này chuyển sang cookie thì phải bật lại CSRF.
 */
@Configuration // class cấu hình: Spring đọc các @Bean bên trong khi khởi động
@EnableMethodSecurity // bật @PreAuthorize để kiểm tra quyền ngay trên method của controller
@RequiredArgsConstructor // Lombok tạo constructor nhận 2 field final bên dưới
public class SecurityConfig {

  /** Prefix version của mọi REST API (khớp {@code api.prefix} trong application.yml). */
  private static final String API = "/api/v1";

  /** Hạ tầng: health check cho Docker/Render và tài liệu OpenAPI. Công khai với mọi method. */
  private static final String[] PUBLIC_INFRASTRUCTURE = {
    "/actuator/health", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
  };

  /**
   * Đọc công khai (chỉ GET): catalog, review, trang shop. Dấu {@code *} khớp đúng một đoạn đường dẫn.
   * IPN và Return URL của VNPAY cũng công khai vì VNPAY và trình duyệt gọi tới không có token; chúng tự
   * xác thực bằng chữ ký VNPAY.
   */
  private static final String[] PUBLIC_GET = {
    API + "/products",
    API + "/products/*",
    API + "/products/*/reviews",
    API + "/categories",
    API + "/sellers/*",
    API + "/payments/vnpay/ipn",
    API + "/payments/vnpay/return"
  };

  /**
   * Ghi công khai (chỉ POST): người gọi chưa có access token. Đăng ký, đăng nhập, quên và đặt lại mật
   * khẩu (mã trong email thay cho token), refresh (dùng refresh token vì access token đã hết hạn), và
   * AI hỏi đáp cho khách.
   */
  private static final String[] PUBLIC_POST = {
    API + "/auth/register",
    API + "/auth/login",
    API + "/auth/refresh",
    API + "/auth/password/forgot",
    API + "/auth/password/reset",
    API + "/ai/ask"
  };

  /** Chỉ customer: giỏ hàng, checkout, order, thanh toán lại, kiểm tra voucher và viết đánh giá. */
  private static final String[] CUSTOMER_ONLY = {
    API + "/cart/**",
    API + "/orders/**",
    API + "/payments/**",
    API + "/vouchers/**",
    // Chỉ POST (tạo đánh giá) rơi vào đây: GET đã được PUBLIC_GET ở trên cho qua trước.
    API + "/products/*/reviews"
  };

  /** Chỉ seller. Trạng thái APPROVED không nằm trong token nên service kiểm tra thêm trong DB. */
  private static final String[] SELLER_ONLY = {API + "/seller/**"};

  /** Chỉ admin. */
  private static final String[] ADMIN_ONLY = {API + "/admin/**"};

  /** CORS: method và header trình duyệt được gửi; thời gian (giây) trình duyệt nhớ kết quả preflight. */
  private static final List<String> CORS_METHODS = List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
  private static final List<String> CORS_HEADERS = List.of("Authorization", "Content-Type");
  private static final long CORS_MAX_AGE_SECONDS = 3600L;

  // Dùng để kiểm tra token, được đưa vào JwtAuthenticationFilter.
  private final JwtService jwtService;
  // Trả lỗi 401 (chưa đăng nhập) và 403 (sai quyền) theo khung ApiResponse.
  private final RestSecurityErrorHandler securityErrorHandler;

  /**
   * Xây dựng chuỗi filter bảo vệ tất cả request HTTP.
   *
   * @param http builder Spring Security cung cấp để cấu hình filter chain
   * @return filter chain được Spring Security dùng cho mọi request
   * @throws Exception nếu Spring Security không xây dựng được chain
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable) // Bước 1: tắt CSRF vì dùng bearer token, không dùng cookie
        .cors(Customizer.withDefaults()) // Bước 2: bật CORS theo bean corsConfigurationSource bên dưới
        // Bước 3: STATELESS = server không nhớ ai qua session, mỗi request tự mang token.
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Bước 4: chưa đăng nhập -> 401, sai quyền -> 403, cả hai đều trả dạng ApiResponse.
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler))
        // Bước 5: filter JWT đứng trước filter đăng nhập mặc định của Spring. Dùng "new" thay vì
        // @Component để filter không bị đăng ký hai lần.
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        // Bước 6: luật URL. Spring dùng luật ĐẦU TIÊN khớp, nên anyRequest() phải nằm cuối.
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_INFRASTRUCTURE).permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                    .requestMatchers(CUSTOMER_ONLY).hasRole(Role.CUSTOMER.name())
                    .requestMatchers(SELLER_ONLY).hasRole(Role.SELLER.name())
                    .requestMatchers(ADMIN_ONLY).hasRole(Role.ADMIN.name())
                    // Mọi request còn lại (ví dụ /users/me, /auth/logout) phải đăng nhập, thiếu token -> 401.
                    .anyRequest().authenticated());
    return http.build();
  }

  /**
   * Cấu hình CORS đọc từ {@code app.cors.allowed-origin-patterns} (biến {@code
   * APP_CORS_ALLOWED_ORIGIN_PATTERNS}, danh sách pattern phân cách bằng dấu phẩy).
   *
   * <p>Mặc định rỗng nghĩa là không origin nào được phép. Chỉ đặt giá trị này khi cần gọi API từ trình
   * duyệt, ví dụ {@code *} khi phát triển; production nên liệt kê đích danh origin của web app. Không bật
   * {@code allowCredentials} vì API dùng bearer token, không dùng cookie.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origin-patterns:}") List<String> allowedOriginPatterns) {
    // Bảng quy tắc CORS theo đường dẫn; để trống nghĩa là chặn mọi origin lạ.
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // Chỉ khi có đặt ít nhất một origin (không rỗng) thì mới thêm quy tắc cho phép.
    if (allowedOriginPatterns.stream().anyMatch(p -> !p.isBlank())) {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOriginPatterns(allowedOriginPatterns.stream().map(String::trim).toList());
      config.setAllowedMethods(CORS_METHODS);
      config.setAllowedHeaders(CORS_HEADERS);
      config.setMaxAge(CORS_MAX_AGE_SECONDS);
      source.registerCorsConfiguration("/**", config); // áp dụng cho mọi đường dẫn
    }
    return source;
  }

  /**
   * Encoder dùng chung để lưu và kiểm tra password hash.
   *
   * <p>Đăng ký gọi {@code encode(rawPassword)} trước khi lưu; đăng nhập dùng {@code matches(raw,
   * storedHash)}. Không encode rồi so hai chuỗi hash, vì BCrypt tạo salt mới mỗi lần encode.
   *
   * @return encoder BCrypt dùng chung trong Spring context
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    // BCrypt: băm một chiều và tự thêm "muối" ngẫu nhiên, cùng mật khẩu cho ra hash khác nhau mỗi lần.
    return new BCryptPasswordEncoder();
  }
}
