package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.marketplace.repository.CartItemRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.support.TestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cache đọc giỏ hàng bằng Redis (architecture §5.4). Redis được thay bằng một Map trong bộ nhớ, đủ để
 * kiểm tra luồng: đọc lần hai lấy từ cache, sửa giỏ thì xoá cache sau commit, lỗi thì không xoá, và Redis
 * chết thì giỏ vẫn đúng.
 */
@SpringBootTest(properties = "app.cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartCacheApiTest {

  private static final String CART = "/api/v1/cart";
  private static final String ITEMS = "/api/v1/cart/items";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;
  // Spy: repository thật, chỉ để đếm số lần đọc giỏ từ PostgreSQL.
  @MockitoSpyBean private CartItemRepository cartItems;
  @MockitoBean private StringRedisTemplate redis;

  /** "Redis" giả: key -> Hash. */
  private final Map<String, Map<Object, Object>> store = new ConcurrentHashMap<>();

  private User customer;
  private Product phone; // 59.00
  private Product charger; // 50.00

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    // Mock được tạo lại sau mỗi test, nên nối Map vào các lệnh Redis ở đây.
    HashOperations<String, Object, Object> hash = mock(HashOperations.class);
    doReturn(hash).when(redis).opsForHash();
    when(hash.entries(anyString()))
        .thenAnswer(call -> new HashMap<>(store.getOrDefault(call.getArgument(0), Map.of())));
    doAnswer(
            call -> {
              store.computeIfAbsent(call.getArgument(0), k -> new HashMap<>()).putAll(call.getArgument(1));
              return null;
            })
        .when(hash)
        .putAll(anyString(), anyMap());
    when(redis.delete(anyString())).thenAnswer(call -> store.remove((String) call.getArgument(0)) != null);

    customer = support.user(Role.CUSTOMER, null);
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    Category category = support.category();
    phone = support.product(seller, category, "Phone", "59.00", 10);
    charger = support.product(seller, category, "Charger", "50.00", 10);
  }

  @Test
  void theSecondReadComesFromTheCacheButPricesAreAlwaysCurrent() throws Exception {
    support.addToCart(mvc, customer, phone, 1);
    support.addToCart(mvc, customer, charger, 2);
    clearInvocations(cartItems);

    // Lần đầu: cache trống, đọc PostgreSQL rồi ghi bản sao có thời gian sống 30 phút.
    String fromDb = cart().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(store).containsKey(key());
    verify(redis).expire(eq(key()), eq(Duration.ofMinutes(30)));

    // Seller đổi giá sau khi giỏ đã được cache: giỏ vẫn thấy giá mới, vì product không nằm trong cache.
    phone.setPrice(new BigDecimal("49.00"));
    products.save(phone);
    String fromCache =
        cart()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].unitPrice").value("49.00"))
            .andExpect(jsonPath("$.data.items[1].quantity").value(1))
            .andExpect(jsonPath("$.data.subtotal").value("149.00"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Lần hai lấy dòng giỏ từ cache: chỉ đúng một lần đọc PostgreSQL, và thứ tự dòng giữ nguyên.
    verify(cartItems, times(1)).findByUserIdOrderByCreatedAtDescIdAsc(customer.getId());
    List<String> idsFromDb = JsonPath.read(fromDb, "$.data.items[*].id");
    List<String> idsFromCache = JsonPath.read(fromCache, "$.data.items[*].id");
    assertThat(idsFromCache).isEqualTo(idsFromDb);
  }

  @Test
  void changesClearTheCacheAfterCommitButAFailedChangeKeepsIt() throws Exception {
    String line = support.addToCart(mvc, customer, phone, 1);
    cart().andExpect(status().isOk());
    assertThat(store).containsKey(key());

    // Sửa thành công: cache bị xoá, lần đọc sau thấy số lượng mới.
    support.send(mvc, patch(ITEMS + "/" + line), customer, "{\"quantity\":3}").andExpect(status().isOk());
    assertThat(store).doesNotContainKey(key());
    cart().andExpect(jsonPath("$.data.items[0].quantity").value(3));

    // Thêm quá tồn kho: 409, transaction rollback, PostgreSQL không đổi nên cache cũng giữ nguyên.
    support
        .send(mvc, post(ITEMS), customer, "{\"productId\":\"" + phone.getId() + "\",\"quantity\":100}")
        .andExpect(status().isConflict());
    assertThat(store).containsKey(key());

    // Xoá dòng: cache bị xoá, giỏ trống.
    mvc.perform(delete(ITEMS + "/" + line).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isOk());
    assertThat(store).doesNotContainKey(key());
    cart().andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void checkoutClearsTheCachedCart() throws Exception {
    String bought = support.addToCart(mvc, customer, phone, 1);
    String kept = support.addToCart(mvc, customer, charger, 1);
    cart().andExpect(jsonPath("$.data.items.length()").value(2));

    support
        .send(
            mvc,
            post("/api/v1/orders"),
            customer,
            "{\"cartItemIds\":[\"" + bought + "\"],\"shippingAddress\":\"12 Le Loi\",\"paymentMethod\":\"COD\"}")
        .andExpect(status().isCreated());

    assertThat(store).doesNotContainKey(key());
    cart()
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(kept));
  }

  @Test
  void theCartStillWorksWhenRedisIsDown() throws Exception {
    doThrow(new RedisConnectionFailureException("down")).when(redis).opsForHash();
    when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

    support.addToCart(mvc, customer, phone, 2);
    cart()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].productId").value(phone.getId().toString()))
        .andExpect(jsonPath("$.data.items[0].quantity").value(2))
        .andExpect(jsonPath("$.data.subtotal").value("118.00"));
    verify(redis, times(0)).expire(any(), any(Duration.class)); // ghi cache thất bại thì bỏ qua
  }

  private ResultActions cart() throws Exception {
    return mvc.perform(get(CART).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)));
  }

  private String key() {
    return "cart:" + customer.getId();
  }
}
