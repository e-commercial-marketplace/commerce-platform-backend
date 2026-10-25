package com.marketplace.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.entity.Category;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.support.TestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cache danh sách category và trang shop (architecture §5.4). Redis được thay bằng cache trong bộ nhớ, cũng
 * ghi và xoá sau commit như RedisCacheManager.transactionAware(), để kiểm tra annotation: đọc lần hai lấy
 * từ cache, và mọi thay đổi của admin hay seller hiện ra ngay ở lần đọc sau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogCacheApiTest {

  @TestConfiguration
  static class InMemoryCache {
    @Bean
    @Primary
    CacheManager inMemoryCacheManager() {
      return new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager());
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private CacheManager cacheManager;
  // Spy: repository thật, chỉ để đếm số lần đọc PostgreSQL.
  @MockitoSpyBean private CategoryRepository categories;
  @MockitoSpyBean private ProductRepository products;

  private User admin;
  private User seller;
  private Category category;

  @BeforeEach
  void setUp() {
    // Cache trong bộ nhớ sống suốt context: xoá đi để mỗi test bắt đầu từ cache trống.
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    admin = support.user(Role.ADMIN, null);
    seller = support.user(Role.SELLER, SellerStatus.APPROVED, "Shop Cache");
    category = support.category();
  }

  @Test
  void theCategoryListIsCachedAndAnAdminChangeShowsOnTheNextRead() throws Exception {
    clearInvocations(categories);
    categoryList().andExpect(status().isOk());
    categoryList().andExpect(status().isOk());
    verify(categories, times(1)).findAllByOrderByNameAsc(); // lần hai lấy từ cache

    String name = "Cached-" + UUID.randomUUID();
    support.send(mvc, post("/api/v1/admin/categories"), admin, "{\"name\":\"" + name + "\"}")
        .andExpect(status().isCreated());
    categoryList().andExpect(jsonPath("$.data[*].name", hasItem(name)));
    verify(categories, times(2)).findAllByOrderByNameAsc(); // cache đã bị xoá sau commit

    // Tạo trùng tên: 400, transaction rollback, cache không bị xoá.
    support.send(mvc, post("/api/v1/admin/categories"), admin, "{\"name\":\"" + name + "\"}")
        .andExpect(status().isBadRequest());
    categoryList().andExpect(status().isOk());
    verify(categories, times(2)).findAllByOrderByNameAsc();
  }

  @Test
  void theShopPageIsCachedAndSellerOrAdminChangesShowOnTheNextRead() throws Exception {
    support.product(seller, category, "First", "10.00", 1);
    clearInvocations(products);
    shop().andExpect(jsonPath("$.data.productCount").value(1));
    shop().andExpect(jsonPath("$.data.productCount").value(1));
    verify(products, times(1)).countBySellerIdAndActiveTrue(seller.getId()); // lần hai lấy từ cache

    // Seller đăng thêm product: số product đang bán tăng ngay.
    support
        .send(
            mvc,
            post("/api/v1/seller/products"),
            seller,
            "{\"name\":\"Second\",\"price\":\"20.00\",\"stock\":1,\"categoryId\":\"" + category.getId()
                + "\",\"imageUrl\":\"https://example.com/p.jpg\"}")
        .andExpect(status().isCreated());
    shop().andExpect(jsonPath("$.data.productCount").value(2));

    // Seller đổi tên (tên shop là tên seller).
    support.send(mvc, patch("/api/v1/users/me"), seller, "{\"name\":\"Shop Renamed\"}").andExpect(status().isOk());
    shop().andExpect(jsonPath("$.data.name").value("Shop Renamed"));

    // Admin khoá seller: trang shop thành 404 ngay; 404 không được cache nên duyệt lại thì hiện lại ngay.
    support.send(mvc, patch("/api/v1/admin/sellers/" + seller.getId() + "/suspend"), admin, "{}")
        .andExpect(status().isOk());
    shop().andExpect(status().isNotFound());
    support.send(mvc, patch("/api/v1/admin/sellers/" + seller.getId() + "/approve"), admin, "{}")
        .andExpect(status().isOk());
    shop().andExpect(status().isOk()).andExpect(jsonPath("$.data.productCount").value(2));
  }

  private ResultActions categoryList() throws Exception {
    return mvc.perform(get("/api/v1/categories"));
  }

  private ResultActions shop() throws Exception {
    return mvc.perform(get("/api/v1/sellers/" + seller.getId()));
  }
}
