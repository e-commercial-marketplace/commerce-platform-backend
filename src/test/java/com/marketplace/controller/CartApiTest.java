package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.support.TestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử giỏ hàng theo features/cart/API_CONTRACT.md: thêm và cộng dồn, sửa, xoá, kiểm tồn kho và
 * product còn bán, giá tính lại theo product hiện tại, quyền sở hữu và chỉ CUSTOMER được dùng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartApiTest {

  private static final String CART = "/api/v1/cart";
  private static final String ITEMS = "/api/v1/cart/items";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;

  private User customer;
  private User seller;
  private Category category;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    category = categories.save(Category.create("Cat-" + UUID.randomUUID()));
  }

  // ---------- xem giỏ ----------

  @Test
  void anEmptyCartHasNoItemsAndZeroSubtotal() throws Exception {
    cart(customer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0))
        .andExpect(jsonPath("$.data.subtotal").value("0.00"));
  }

  // ---------- thêm ----------

  @Test
  void addingAProductReturnsTheCartWithCurrentPriceAndLineTotal() throws Exception {
    Product headphone = product("Headphone", "59.00", 12);

    add(customer, headphone, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].productId").value(headphone.getId().toString()))
        .andExpect(jsonPath("$.data.items[0].name").value("Headphone"))
        .andExpect(jsonPath("$.data.items[0].unitPrice").value("59.00"))
        .andExpect(jsonPath("$.data.items[0].quantity").value(2))
        .andExpect(jsonPath("$.data.items[0].lineTotal").value("118.00"))
        .andExpect(jsonPath("$.data.items[0].availableStock").value(12))
        .andExpect(jsonPath("$.data.items[0].active").value(true))
        .andExpect(jsonPath("$.data.items[0].sellerId").value(seller.getId().toString()))
        .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.subtotal").value("118.00"));
  }

  @Test
  void addingTheSameProductAgainIncrementsTheExistingRow() throws Exception {
    Product p = product("Mouse", "10.00", 5);

    add(customer, p, 2).andExpect(status().isOk());
    add(customer, p, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1)) // vẫn một dòng
        .andExpect(jsonPath("$.data.items[0].quantity").value(5))
        .andExpect(jsonPath("$.data.subtotal").value("50.00"));

    // Cộng dồn vượt tồn kho (5 + 1 > 5) thì 409, giỏ giữ nguyên.
    add(customer, p, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
    cart(customer).andExpect(jsonPath("$.data.items[0].quantity").value(5));
  }

  @Test
  void itemsAreListedNewestFirst() throws Exception {
    Product first = product("First", "1.00", 9);
    Product second = product("Second", "2.00", 9);

    add(customer, first, 1);
    add(customer, second, 1);
    // Cộng dồn vào dòng cũ không đưa nó lên đầu.
    add(customer, first, 1)
        .andExpect(jsonPath("$.data.items[0].name").value("Second"))
        .andExpect(jsonPath("$.data.items[1].name").value("First"));
  }

  @Test
  void addRejectsInactiveUnknownAndTooManyProducts() throws Exception {
    Product hidden = product("Hidden", "5.00", 9);
    hidden.setActive(false);
    products.save(hidden);

    add(customer, hidden, 1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_INACTIVE"));
    send(post(ITEMS), customer, "{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    add(customer, product("Few", "5.00", 2), 3)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
    // Số lượng cực lớn không bị tràn số thành âm rồi lọt qua kiểm tra tồn kho.
    Product p = product("Overflow", "5.00", 9);
    add(customer, p, 1);
    add(customer, p, Integer.MAX_VALUE).andExpect(status().isConflict());

    cart(customer).andExpect(jsonPath("$.data.items.length()").value(1));
  }

  @Test
  void invalidAddBodiesReturn400() throws Exception {
    Product p = product("P", "5.00", 9);
    String[] invalidBodies = {
      "{\"quantity\":1}", // thiếu productId
      "{\"productId\":\"" + p.getId() + "\"}", // thiếu quantity
      "{\"productId\":\"" + p.getId() + "\",\"quantity\":0}",
      "{\"productId\":\"" + p.getId() + "\",\"quantity\":-1}",
      "{\"productId\":\"not-a-uuid\",\"quantity\":1}"
    };
    for (String body : invalidBodies) {
      send(post(ITEMS), customer, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  // ---------- sửa và xoá ----------

  @Test
  void updateSetsTheQuantityAndChecksStockAndActivity() throws Exception {
    Product p = product("Keyboard", "20.00", 4);
    String itemId = firstItemId(add(customer, p, 1));

    send(patch(ITEMS + "/" + itemId), customer, "{\"quantity\":4}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].quantity").value(4))
        .andExpect(jsonPath("$.data.subtotal").value("80.00"));
    send(patch(ITEMS + "/" + itemId), customer, "{\"quantity\":5}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
    send(patch(ITEMS + "/" + itemId), customer, "{\"quantity\":0}").andExpect(status().isBadRequest());

    p.setActive(false);
    products.save(p);
    send(patch(ITEMS + "/" + itemId), customer, "{\"quantity\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_INACTIVE"));
  }

  @Test
  void deleteRemovesTheRowAndReturnsTheCart() throws Exception {
    String keep = firstItemId(add(customer, product("Keep", "3.00", 9), 1));
    String remove = firstItemId(add(customer, product("Remove", "4.00", 9), 1));

    mvc.perform(delete(ITEMS + "/" + remove).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(keep))
        .andExpect(jsonPath("$.data.subtotal").value("3.00"));
  }

  @Test
  void anotherCustomersItemIs403AndAnUnknownItemIs404() throws Exception {
    String theirs = firstItemId(add(customer, product("Theirs", "5.00", 9), 1));
    User other = support.user(Role.CUSTOMER, null);

    // Theo contract: dòng có thật nhưng của người khác thì 403.
    send(patch(ITEMS + "/" + theirs), other, "{\"quantity\":2}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    mvc.perform(delete(ITEMS + "/" + theirs).header(HttpHeaders.AUTHORIZATION, support.bearer(other)))
        .andExpect(status().isForbidden());
    // Không có dòng nào với id đó thì 404 CART_ITEM_NOT_FOUND.
    send(patch(ITEMS + "/" + UUID.randomUUID()), customer, "{\"quantity\":1}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CART_ITEM_NOT_FOUND"));
    // Giỏ của chủ không đổi, và người kia không thấy dòng đó trong giỏ của mình.
    cart(customer).andExpect(jsonPath("$.data.items[0].quantity").value(1));
    cart(other).andExpect(jsonPath("$.data.items.length()").value(0));
  }

  // ---------- giá và trạng thái lấy từ product hiện tại ----------

  @Test
  void pricesAndAvailabilityFollowTheCurrentProduct() throws Exception {
    Product price = product("Price", "10.00", 9);
    Product hidden = product("Hidden later", "7.00", 9);
    Product scarce = product("Scarce later", "3.00", 9);
    add(customer, price, 2);
    add(customer, hidden, 1);
    add(customer, scarce, 3);

    // Seller đổi giá, ẩn một product, giảm tồn kho của product khác dưới số lượng trong giỏ.
    price.setPrice(new BigDecimal("12.50"));
    hidden.setActive(false);
    scarce.setStock(2);
    products.save(price);
    products.save(hidden);
    products.save(scarce);

    cart(customer)
        .andExpect(jsonPath("$.data.items.length()").value(3)) // các dòng vẫn còn trong giỏ
        .andExpect(jsonPath("$.data.items[?(@.name=='Price')].unitPrice").value("12.50"))
        .andExpect(jsonPath("$.data.items[?(@.name=='Price')].lineTotal").value("25.00"))
        .andExpect(jsonPath("$.data.items[?(@.name=='Hidden later')].active").value(false))
        .andExpect(jsonPath("$.data.items[?(@.name=='Scarce later')].availableStock").value(2))
        // subtotal chỉ cộng các dòng mua được: 25.00 (Hidden bị ẩn, Scarce thiếu hàng)
        .andExpect(jsonPath("$.data.subtotal").value("25.00"));

    // Dòng không mua được vẫn xoá được để khách dọn giỏ.
    // Biểu thức lọc [?(...)] luôn trả về một danh sách, nên lấy phần tử đầu.
    java.util.List<String> hiddenIds =
        JsonPath.read(
            cart(customer).andReturn().getResponse().getContentAsString(),
            "$.data.items[?(@.name=='Hidden later')].id");
    String hiddenItem = hiddenIds.get(0);
    mvc.perform(delete(ITEMS + "/" + hiddenItem).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isOk());
  }

  // ---------- phân quyền ----------

  @Test
  void onlyCustomersCanUseTheCart() throws Exception {
    mvc.perform(get(CART)).andExpect(status().isUnauthorized());

    User admin = support.user(Role.ADMIN, null);
    for (User notCustomer : new User[] {seller, admin}) {
      cart(notCustomer)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
      add(notCustomer, product("X", "1.00", 9), 1).andExpect(status().isForbidden());
    }
  }

  // ---------- hàm hỗ trợ ----------

  private Product product(String name, String price, int stock) {
    return products.save(Product.create(name, "d", null, new BigDecimal(price), stock, category, seller));
  }

  private ResultActions cart(User who) throws Exception {
    return mvc.perform(
        get(CART)
            .header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private ResultActions add(User who, Product product, int quantity) throws Exception {
    return send(post(ITEMS), who, "{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity + "}");
  }

  private ResultActions send(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      User who,
      String json)
      throws Exception {
    return support.send(mvc, request.contentType(MediaType.APPLICATION_JSON), who, json);
  }

  private static String firstItemId(ResultActions result) throws Exception {
    // Dòng vừa thêm là dòng mới nhất nên đứng đầu giỏ.
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
  }
}
