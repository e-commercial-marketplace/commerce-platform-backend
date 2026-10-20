package com.marketplace.support;

import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.JwtService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Hàm hỗ trợ dùng chung cho các test tích hợp: tạo người dùng, dựng token và gửi request có body JSON.
 *
 * <p>Trước đây các hàm này được viết lại ở từng class test. Class này là một bean nằm trong thư mục
 * test nên chỉ cần {@code @Autowired} là dùng được, không ảnh hưởng code chạy thật.
 */
@Component
public class TestSupport {

  private final UserRepository users; // lưu người dùng thật vào H2 của test
  private final JwtService jwtService; // tạo access token đúng như app thật

  public TestSupport(UserRepository users, JwtService jwtService) {
    this.users = users;
    this.jwtService = jwtService;
  }

  /** Tạo và lưu một người dùng với email ngẫu nhiên (tránh trùng giữa các test trong cùng H2). */
  public User user(Role role, SellerStatus sellerStatus) {
    return user(role, sellerStatus, "Test");
  }

  /**
   * Như {@link #user(Role, SellerStatus)} nhưng đặt tên hiển thị. Với SELLER thì ghi đè trạng thái duyệt
   * (vì {@code User.register} mặc định là PENDING); các role khác bỏ qua {@code sellerStatus}.
   */
  public User user(Role role, SellerStatus sellerStatus, String name) {
    User user = User.register("u-" + UUID.randomUUID() + "@test.com", "hash", name, role);
    if (role == Role.SELLER) {
      user.setSellerStatus(sellerStatus);
    }
    return users.save(user);
  }

  /** Giá trị header {@code Authorization} với access token thật của người dùng. */
  public String bearer(User user) {
    return "Bearer " + jwtService.generateAccessToken(user);
  }

  /** Gửi một request có body JSON, kèm token của người dùng {@code who}. */
  public ResultActions send(
      MockMvc mvc, MockHttpServletRequestBuilder request, User who, String json) throws Exception {
    return mvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, bearer(who))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }
}
