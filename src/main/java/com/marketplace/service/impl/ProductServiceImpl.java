package com.marketplace.service.impl;

import static com.marketplace.repository.ProductSpecifications.inCategory;
import static com.marketplace.repository.ProductSpecifications.activeIs;
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
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

  @Override
  @Transactional(readOnly = true)
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

    Specification<Product> spec = activeIs(true); // công khai chỉ thấy product đang bán
    if (text != null) {
      spec = spec.and(textMatches(text));
    }
    if (request.categoryId() != null) {
      spec = spec.and(inCategory(request.categoryId()));
    }
    // Xem shop của một seller: chỉ giữ product của seller đó (vẫn chỉ là product đang bán, vì spec activeIs(true) ở trên).
    if (request.sellerId() != null) {
      spec = spec.and(soldBy(request.sellerId()));
    }
    if (request.minPrice() != null) {
      spec = spec.and(priceAtLeast(request.minPrice()));
    }
    if (request.maxPrice() != null) {
      spec = spec.and(priceAtMost(request.maxPrice()));
    }
    if (request.minRating() != null) {
      spec = spec.and(ratingAtLeast(request.minRating()));
    }

    Pageable pageable;
    if (sort == ProductSort.RELEVANCE) {
      // Thứ tự relevance do specification đặt ORDER BY; Pageable phải không kèm sort.
      spec = spec.and(orderedByRelevance(text));
      pageable = PageRequest.of(request.page(), request.size());
    } else {
      pageable = PageRequest.of(request.page(), request.size(), toSort(sort));
    }

    // Truy vấn một trang rồi đổi từng Product thành ProductSummaryResponse.
    return PageResponse.from(productRepository.findAll(spec, pageable), productMapper::toSummary);
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
