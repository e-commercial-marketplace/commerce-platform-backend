package com.marketplace.service.impl;

import static com.marketplace.repository.ProductSpecifications.inCategory;
import static com.marketplace.repository.ProductSpecifications.activeIs;
import static com.marketplace.repository.ProductSpecifications.idIn;
import static com.marketplace.repository.ProductSpecifications.orderedByRelevance;
import static com.marketplace.repository.ProductSpecifications.priceAtLeast;
import static com.marketplace.repository.ProductSpecifications.priceAtMost;
import static com.marketplace.repository.ProductSpecifications.ratingAtLeast;
import static com.marketplace.repository.ProductSpecifications.soldBy;
import static com.marketplace.repository.ProductSpecifications.textMatches;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.config.CacheConfig;
import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Product;
import com.marketplace.enums.ProductSort;
import com.marketplace.mapper.ProductMapper;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.ProductService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final ProductMapper productMapper;
  private final ProductIndexer productIndexer;

  @Override
  // Không @Transactional: nhánh tìm theo nghĩa gọi provider embedding, không được giữ kết nối DB trong lúc chờ.
  // Mỗi câu findAll tự chạy transaction riêng; category và seller đã được nạp sẵn bằng @EntityGraph.
  // Một trang kết quả sống 60 giây; key là toàn bộ tham số (record nên toString liệt kê đủ các field).
  @Cacheable(cacheNames = CacheConfig.PRODUCT_SEARCH, key = "#request.toString()")
  public PageResponse<ProductSummaryResponse> searchListedProducts(ProductSearchRequest request) {
    if (request.minPrice() != null
        && request.maxPrice() != null
        && request.minPrice().compareTo(request.maxPrice()) > 0) {
      throw BusinessException.validation("minPrice: must not be greater than maxPrice");
    }
    String text = request.q() == null || request.q().isBlank() ? null : request.q().trim();
    ProductSort sort = ProductSort.resolve(request.sort(), text != null);

    // Các bộ lọc trừ từ khoá: dùng chung cho tìm theo chữ và tìm theo nghĩa.
    Specification<Product> filters = activeIs(true); // công khai chỉ thấy product đang bán
    if (request.categoryId() != null) {
      filters = filters.and(inCategory(request.categoryId()));
    }
    // Xem shop của một seller: chỉ giữ product của seller đó (vẫn chỉ là product đang bán, vì spec activeIs(true) ở trên).
    if (request.sellerId() != null) {
      filters = filters.and(soldBy(request.sellerId()));
    }
    if (request.minPrice() != null) {
      filters = filters.and(priceAtLeast(request.minPrice()));
    }
    if (request.maxPrice() != null) {
      filters = filters.and(priceAtMost(request.maxPrice()));
    }
    if (request.minRating() != null) {
      filters = filters.and(ratingAtLeast(request.minRating()));
    }
    Specification<Product> spec = text == null ? filters : filters.and(textMatches(text));

    Pageable pageable;
    if (sort == ProductSort.RELEVANCE) {
      // Thứ tự relevance do specification đặt ORDER BY; Pageable phải không kèm sort.
      spec = spec.and(orderedByRelevance(text));
      pageable = PageRequest.of(request.page(), request.size());
    } else {
      pageable = PageRequest.of(request.page(), request.size(), toSort(sort));
    }

    // Truy vấn một trang; không product nào chứa từ khoá thì thử tìm theo nghĩa.
    Page<Product> page = productRepository.findAll(spec, pageable);
    if (text != null && page.getTotalElements() == 0) {
      page = searchByMeaning(text, filters, sort, request);
    }
    // Đổi từng Product thành ProductSummaryResponse.
    return PageResponse.from(page, productMapper::toSummary);
  }

  /**
   * Fallback khi tìm theo chữ không ra gì: lấy các product gần nghĩa từ {@link ProductIndexer}, áp lại mọi bộ
   * lọc (kể cả active, vì bảng embedding có thể còn product đã ẩn), rồi sắp: {@code relevance} giữ thứ tự gần
   * nghĩa, các kiểu khác sắp như thường.
   */
  private Page<Product> searchByMeaning(
      String text, Specification<Product> filters, ProductSort sort, ProductSearchRequest request) {
    Pageable pageable = PageRequest.of(request.page(), request.size());
    List<UUID> ids = productIndexer.findSimilar(text);
    if (ids.isEmpty()) {
      return Page.empty(pageable);
    }
    Specification<Product> spec = filters.and(idIn(ids));
    List<Product> products;
    if (sort == ProductSort.RELEVANCE) {
      products = new ArrayList<>(productRepository.findAll(spec, Sort.unsorted()));
      products.sort(Comparator.comparingInt(product -> ids.indexOf(product.getId())));
    } else {
      products = productRepository.findAll(spec, toSort(sort));
    }
    // shortcut: phân trang trong bộ nhớ, chỉ ổn vì findSimilar trả tối đa 50 id; nhiều hơn thì phân trang bằng SQL.
    int from = (int) Math.min(pageable.getOffset(), products.size());
    int to = Math.min(from + pageable.getPageSize(), products.size());
    return new PageImpl<>(products.subList(from, to), pageable, products.size());
  }

  @Override
  @Transactional(readOnly = true)
  // 404 là exception nên không được cache: product được bán lại thì hiện ra ngay.
  @Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#id")
  public ProductResponse getListedProduct(UUID id) {
    return productRepository
        .findByIdAndActiveTrue(id)
        .map(productMapper::toResponse)
        .orElseThrow(() -> BusinessException.notFound("Product", id));
  }

  /** {@code id} tăng dần làm tie-breaker để phân trang ổn định. */
  private static Sort toSort(ProductSort sort) {
    Sort byId = Sort.by(Sort.Order.asc("id"));
    return switch (sort) {
      case PRICE_ASC -> Sort.by(Sort.Order.asc("price")).and(byId);
      case PRICE_DESC -> Sort.by(Sort.Order.desc("price")).and(byId);
      case RATING_DESC, RELEVANCE -> Sort.by(Sort.Order.desc("ratingAverage")).and(byId);
    };
  }

  @Override
  public void requireListed(Product product) {
    if (!product.isActive()) {
      throw BusinessException.productInactive("Product is no longer available: " + product.getName());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> categoryIdsWithListedProducts() {
    return productRepository.findCategoryIdsWithActiveProducts();
  }

  // Ba hàm dưới chạy trong transaction của người gọi (checkout, huỷ dòng...). Cache chi tiết bị xoá sau khi
  // transaction đó commit (CacheManager transactionAware); rollback thì không xoá.

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
  public boolean decreaseStock(UUID productId, int quantity) {
    return productRepository.decreaseStock(productId, quantity) == 1;
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
  public void increaseStock(UUID productId, int quantity) {
    productRepository.increaseStock(productId, quantity);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
  public void addRating(UUID productId, int rating) {
    productRepository.addRating(productId, rating);
  }
}
