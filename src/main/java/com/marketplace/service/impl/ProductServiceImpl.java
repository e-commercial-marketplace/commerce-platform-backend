package com.marketplace.service.impl;

import static com.marketplace.repository.ProductSpecifications.inCategory;
import static com.marketplace.repository.ProductSpecifications.isActive;
import static com.marketplace.repository.ProductSpecifications.orderedByRelevance;
import static com.marketplace.repository.ProductSpecifications.priceAtLeast;
import static com.marketplace.repository.ProductSpecifications.priceAtMost;
import static com.marketplace.repository.ProductSpecifications.ratingAtLeast;
import static com.marketplace.repository.ProductSpecifications.soldBy;
import static com.marketplace.repository.ProductSpecifications.textMatches;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Product;
import com.marketplace.enums.ProductSort;
import com.marketplace.mapper.ProductMapper;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.ProductService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final ProductMapper productMapper;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> search(ProductSearchRequest request) {
    if (request.minPrice() != null
        && request.maxPrice() != null
        && request.minPrice().compareTo(request.maxPrice()) > 0) {
      throw new BusinessException(
          "VALIDATION_ERROR", "minPrice: must not be greater than maxPrice", HttpStatus.BAD_REQUEST);
    }
    String text = request.q() == null || request.q().isBlank() ? null : request.q().trim();
    ProductSort sort = ProductSort.resolve(request.sort(), text != null);

    Specification<Product> spec = isActive();
    if (text != null) {
      spec = spec.and(textMatches(text));
    }
    if (request.categoryId() != null) {
      spec = spec.and(inCategory(request.categoryId()));
    }
    // Xem shop của một seller: chỉ giữ product của seller đó (vẫn chỉ là product đang bán, vì spec isActive ở trên).
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
  public ProductResponse getActiveProduct(UUID id) {
    return productRepository
        .findByIdAndActiveTrue(id)
        .map(productMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Product", id));
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
}
