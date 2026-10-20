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

  // Phần đầu của header Authorization khi gửi token. Có dấu cách ở cuối: "Bearer " là 7 ký tự.
  private static final String BEARER_PREFIX = "Bearer ";

  // Công cụ kiểm tra token. Filter không tự biết giải mã token, nó nhờ JwtService làm việc đó.
  private final JwtService jwtService;

  // Constructor nhận JwtService từ bên ngoài. SecurityConfig gọi: new JwtAuthenticationFilter(jwtService).
  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService; // cất lại để dùng trong doFilterInternal
  }

  // Spring gọi hàm này cho MỖI request đi qua filter (OncePerRequestFilter đảm bảo chỉ một lần/request).
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, // request client gửi lên: dùng để đọc header, URL...
      HttpServletResponse response, // response sẽ trả về: filter này không ghi gì vào đây
      FilterChain chain) // phần còn lại của chuỗi: các filter phía sau rồi tới controller
      throws ServletException, IOException {
    // Bước 1: đọc giá trị header "Authorization". Client không gửi header này thì header == null.
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);

    // Bước 2: chỉ xử lý khi có header VÀ nó bắt đầu bằng "Bearer ".
    // Ngược lại bỏ qua cả khối bên dưới, request đi tiếp như người chưa đăng nhập.
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      jwtService
          // Bước 3: cắt bỏ 7 ký tự "Bearer " để còn lại token thật, rồi nhờ JwtService kiểm tra.
          // parseAccessToken trả về Optional<AuthUser>: có người dùng nếu token hợp lệ,
          // rỗng nếu token sai chữ ký, hết hạn hoặc hỏng.
          .parseAccessToken(header.substring(BEARER_PREFIX.length()))
          // Bước 4: chỉ khi Optional CÓ giá trị (token hợp lệ) thì mới chạy lambda bên dưới.
          // "user" chính là AuthUser vừa được giải mã từ token.
          .ifPresent(
              user ->
                  // Bước 5: SecurityContext là nơi lưu "ai đang gọi" của request hiện tại
                  // (mỗi request có ngăn riêng, không lẫn với request khác).
                  SecurityContextHolder.getContext()
                      // Bước 6: ghi người dùng vào. Từ giờ Spring coi request này là đã đăng nhập.
                      .setAuthentication(
                          // "Thẻ xác thực" 3 tham số (bản 3 tham số = đã xác thực):
                          //   user              -> ai đang gọi
                          //   null              -> mật khẩu, không cần nữa vì token đã chứng minh
                          //   user.authorities()-> được làm gì, ví dụ ROLE_CUSTOMER
                          new UsernamePasswordAuthenticationToken(user, null, user.authorities())));
    }

    // Bước 7: LUÔN chuyển request cho filter kế tiếp (cuối cùng là controller), dù token tốt hay xấu.
    // Thiếu dòng này thì request dừng lại ở đây và client không nhận được phản hồi nào.
    chain.doFilter(request, response);
  }
}
