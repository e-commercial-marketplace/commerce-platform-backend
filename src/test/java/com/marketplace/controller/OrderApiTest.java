package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.OrderRepository;
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
 * Kiểm thử checkout và order theo features/order/API_CONTRACT.md: chỉ mua dòng giỏ được chọn, một
 * order nhiều seller, chụp tên và giá, trừ kho có điều kiện, payment COD chờ thu, và lỗi thì không
 * thay đổi gì (không order, kho và giỏ giữ nguyên).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

  private static final String ORDERS = "/api/v1/orders";
  private static final String ADDRESS = "123 Nguyen Trai, District 1, Ho Chi Minh City";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;
  @Autowired private OrderRepository orders;

  private User customer;
  private User sellerA;
  private User sellerB;
  private Category category;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    sellerA = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop A");
    sellerB = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop B");
    category = support.category();
  }

  // ---------- checkout thành công ----------

  @Test
  void checkoutOfTwoSellersCreatesOneOrderFromTheSelectedLinesOnly() throws Exception {
    Product headphone = product(sellerA, "Headphone", "59.00", 10);
    Product mouse = product(sellerB, "Mouse", "10.50", 5);
    Product notChosen = product(sellerA, "Not chosen", "1.00", 3);
    String line1 = addToCart(headphone, 2);
    String line2 = addToCart(mouse, 3);
    addToCart(notChosen, 1);

    checkout(customer, line1, line2)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
        .andExpect(jsonPath("$.data.shippingAddress").value(ADDRESS))
        .andExpect(jsonPath("$.data.shippingFee").value("0.00"))
        .andExpect(jsonPath("$.data.discountAmount").value("0.00"))
        // 2 x 59.00 + 3 x 10.50 = 149.50
        .andExpect(jsonPath("$.data.totalAmount").value("149.50"))
        .andExpect(jsonPath("$.data.voucherCode").doesNotExist())
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        // Một order, hai dòng hàng của hai seller khác nhau, theo thứ tự khách chọn.
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].productName").value("Headphone"))
        .andExpect(jsonPath("$.data.items[0].productId").value(headphone.getId().toString()))
        .andExpect(jsonPath("$.data.items[0].unitPrice").value("59.00"))
        .andExpect(jsonPath("$.data.items[0].quantity").value(2))
        .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.items[0].sellerId").value(sellerA.getId().toString()))
        .andExpect(jsonPath("$.data.items[1].sellerId").value(sellerB.getId().toString()));

    // Kho đã bị trừ đúng số lượng.
    assertThat(stockOf(headphone)).isEqualTo(8);
    assertThat(stockOf(mouse)).isEqualTo(2);
    assertThat(stockOf(notChosen)).isEqualTo(3);
    // Hai dòng đã mua rời giỏ, dòng không chọn vẫn ở lại.
    cart()
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("Not chosen"));
  }

  @Test
  void anOrderKeepsTheSnapshotWhenTheProductChangesLater() throws Exception {
    Product p = product(sellerA, "Old name", "20.00", 5);
    String orderId = idOf(checkout(customer, addToCart(p, 1)).andExpect(status().isCreated()));

    // Seller đổi tên và giá sau khi khách đã đặt.
    p.setName("New name");
    p.setPrice(new BigDecimal("99.00"));
    products.save(p);

    detail(customer, orderId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].productName").value("Old name"))
        .andExpect(jsonPath("$.data.items[0].unitPrice").value("20.00"))
        .andExpect(jsonPath("$.data.totalAmount").value("20.00"));
  }

  // ---------- checkout bị từ chối: không thay đổi gì ----------

  @Test
  void insufficientStockRejectsTheWholeCheckout() throws Exception {
    Product plenty = product(sellerA, "Plenty", "5.00", 10);
    Product scarce = product(sellerB, "Scarce", "5.00", 3);
    String a = addToCart(plenty, 2);
    String b = addToCart(scarce, 3);
    // Có người khác mua mất hàng sau khi khách đã bỏ vào giỏ.
    scarce.setStock(1);
    products.save(scarce);

    checkout(customer, a, b)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));

    // Hoàn tác toàn bộ: kho của dòng hợp lệ không bị trừ, giỏ còn đủ, không có order nào.
    assertThat(stockOf(plenty)).isEqualTo(10);
    cart().andExpect(jsonPath("$.data.items.length()").value(2));
    assertThat(orders.findByCustomerId(customer.getId(), org.springframework.data.domain.Pageable.unpaged()))
        .isEmpty();
  }

  @Test
  void theLastItemCanBeSoldOnlyOnce() throws Exception {
    Product last = product(sellerA, "Last one", "9.00", 1);
    User other = support.user(Role.CUSTOMER, null);
    String mine = addToCart(last, 1);
    String theirs = addToCart(other, last, 1);

    checkout(customer, mine).andExpect(status().isCreated());
    // Câu UPDATE có điều kiện không cho kho xuống dưới 0.
    checkout(other, theirs)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
    assertThat(stockOf(last)).isZero();
  }

  @Test
  void anInactiveProductIs400() throws Exception {
    Product p = product(sellerA, "Hidden later", "5.00", 5);
    String line = addToCart(p, 1);
    p.setActive(false);
    products.save(p);

    checkout(customer, line)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_INACTIVE"));
    assertThat(stockOf(p)).isEqualTo(5);
  }

  @Test
  void cartLinesOfAnotherCustomerOrUnknownIdsAre404() throws Exception {
    User other = support.user(Role.CUSTOMER, null);
    String theirs = addToCart(other, product(sellerA, "Theirs", "5.00", 5), 1);

    checkout(customer, theirs)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CART_ITEM_NOT_FOUND"));
    checkout(customer, UUID.randomUUID().toString())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CART_ITEM_NOT_FOUND"));
    // Dòng của người kia không bị đụng tới.
    mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, support.bearer(other)))
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }

  @Test
  void invalidRequestsAre400() throws Exception {
    String line = addToCart(product(sellerA, "P", "5.00", 5), 1);
    String[] invalidBodies = {
      "{\"cartItemIds\":[],\"shippingAddress\":\"A\",\"paymentMethod\":\"COD\"}", // không chọn dòng nào
      "{\"cartItemIds\":[\"" + line + "\",\"" + line + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"COD\"}", // trùng
      "{\"cartItemIds\":[\"" + line + "\"],\"shippingAddress\":\"   \",\"paymentMethod\":\"COD\"}", // địa chỉ trống
      "{\"cartItemIds\":[\"" + line + "\"],\"shippingAddress\":\"" + "x".repeat(1001) + "\",\"paymentMethod\":\"COD\"}",
      "{\"cartItemIds\":[\"" + line + "\"],\"shippingAddress\":\"A\"}", // thiếu paymentMethod
      "{\"cartItemIds\":[\"" + line + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"CASH\"}" // không tồn tại
    };
    for (String body : invalidBodies) {
      support.send(mvc, post(ORDERS), customer, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    cart().andExpect(jsonPath("$.data.items.length()").value(1)); // giỏ không đổi
  }

  // ---------- tra cứu order ----------

  @Test
  void ordersAreListedNewestFirstAndPaged() throws Exception {
    Product p = product(sellerA, "P", "1.00", 10);
    String first = idOf(checkout(customer, addToCart(p, 1)));
    String second = idOf(checkout(customer, addToCart(p, 2)));

    mvc.perform(get(ORDERS).param("size", "1").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value(second))
        .andExpect(jsonPath("$.data.content[0].totalAmount").value("2.00"))
        .andExpect(jsonPath("$.data.content[0].paymentMethod").value("COD"));
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data.content[*].id", containsInAnyOrder(first, second)));
  }

  @Test
  void anotherCustomersOrderIs404() throws Exception {
    String orderId = idOf(checkout(customer, addToCart(product(sellerA, "P", "1.00", 5), 1)));
    User other = support.user(Role.CUSTOMER, null);

    detail(other, orderId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    detail(customer, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    // Danh sách của người kia không có order này.
    mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, support.bearer(other)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void onlyCustomersCanCheckoutOrSeeOrders() throws Exception {
    mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
    for (User notCustomer : new User[] {sellerA, support.user(Role.ADMIN, null)}) {
      mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, support.bearer(notCustomer)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
      checkout(notCustomer, UUID.randomUUID().toString()).andExpect(status().isForbidden());
    }
  }

  // ---------- hàm hỗ trợ ----------

  private Product product(User seller, String name, String price, int stock) {
    return support.product(seller, category, name, price, stock);
  }

  private int stockOf(Product product) {
    return products.findById(product.getId()).orElseThrow().getStock();
  }

  /** Thêm vào giỏ của customer chính qua API giỏ hàng; trả id dòng giỏ (dòng mới nhất đứng đầu). */
  private String addToCart(Product product, int quantity) throws Exception {
    return addToCart(customer, product, quantity);
  }

  private String addToCart(User who, Product product, int quantity) throws Exception {
    ResultActions result =
        support.send(
            mvc,
            post("/api/v1/cart/items"),
            who,
            "{\"productId\":\"" + product.getId() + "\",\"quantity\":" + quantity + "}");
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
  }

  private ResultActions checkout(User who, String... cartItemIds) throws Exception {
    String ids = String.join("\",\"", cartItemIds);
    return support.send(
        mvc,
        post(ORDERS),
        who,
        "{\"cartItemIds\":[\"" + ids + "\"],\"shippingAddress\":\"" + ADDRESS + "\",\"paymentMethod\":\"COD\"}");
  }

  private ResultActions cart() throws Exception {
    return mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)));
  }

  private ResultActions detail(User who, String orderId) throws Exception {
    return mvc.perform(get(ORDERS + "/" + orderId).header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
