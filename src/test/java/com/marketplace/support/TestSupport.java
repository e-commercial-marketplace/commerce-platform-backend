package com.marketplace.support;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.JwtService;
import java.math.BigDecimal;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
  private final CategoryRepository categories; // lưu category cho test catalog, giỏ hàng, order
  private final ProductRepository products; // lưu product cho các test đó

  public TestSupport(
      UserRepository users,
      JwtService jwtService,
      CategoryRepository categories,
      ProductRepository products) {
    this.users = users;
    this.jwtService = jwtService;
    this.categories = categories;
    this.products = products;
  }

  /** Tạo và lưu một category với tên ngẫu nhiên (tránh trùng tên giữa các test trong cùng H2). */
  public Category category() {
    return categories.save(Category.builder().name("Cat-" + UUID.randomUUID()).build());
  }

  /**
   * Tạo và lưu một product đang bán của {@code seller}; {@code price} dạng chuỗi như {@code "59.00"}. Có ảnh,
   * vì product đang bán bắt buộc có ảnh (giống ràng buộc trên Postgres thật).
   */
  public Product product(User seller, Category category, String name, String price, int stock) {
    return products.save(
        Product.builder().name(name).description("d").imageUrl("https://example.com/p.jpg").price(new BigDecimal(price)).stock(stock).category(category).seller(seller).active(true).build());
  }

  /** Tạo và lưu một người dùng với email ngẫu nhiên (tránh trùng giữa các test trong cùng H2). */
  public User user(Role role, SellerStatus sellerStatus) {
    return user(role, sellerStatus, "Test");
  }

  /**
   * Như {@link #user(Role, SellerStatus)} nhưng đặt tên hiển thị. Với SELLER thì ghi đè trạng thái duyệt
   * (vì seller mới mặc định là PENDING); các role khác bỏ qua {@code sellerStatus}.
   */
  public User user(Role role, SellerStatus sellerStatus, String name) {
    User user = User.builder().email("u-" + UUID.randomUUID() + "@test.com").passwordHash("hash").fullName(name).role(role).sellerStatus(role == Role.SELLER ? SellerStatus.PENDING : null).build();
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

  /**
   * {@code who} thêm {@code quantity} cái {@code product} vào giỏ và trả về id dòng giỏ vừa thêm (giỏ sắp
   * mới nhất trước nên đó là dòng đầu tiên).
   */
  public String addToCart(MockMvc mvc, User who, Product product, int quantity) throws Exception {
    ResultActions result =
        send(
            mvc,
            post("/api/v1/cart/items"),
            who,
            "{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity + "}");
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
  }
}
