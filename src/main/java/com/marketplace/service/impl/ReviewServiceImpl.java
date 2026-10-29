package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.Pages;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.ReviewCreateRequest;
import com.marketplace.dto.response.ReviewResponse;
import com.marketplace.entity.Product;
import com.marketplace.entity.Review;
import com.marketplace.mapper.ReviewMapper;
import com.marketplace.repository.OrderItemRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.ReviewRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.ProductService;
import com.marketplace.service.ReviewService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đánh giá sản phẩm: điều kiện là order COMPLETED (khách đã xác nhận nhận hàng) chứa product đó. */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final ProductRepository productRepository;
  private final ProductService productService;
  private final OrderItemRepository orderItemRepository;
  private final UserRepository userRepository;
  private final ReviewMapper reviewMapper;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public ReviewResponse create(UUID customerId, UUID productId, ReviewCreateRequest request) {
    // Bước 1: product phải đang bán (không có hoặc đã ẩn thì 404).
    Product product = findVisibleProduct(productId);
    // Bước 2: phải có order COMPLETED chứa dòng chưa huỷ của product này (403), và chưa đánh giá lần nào (409).
    if (!orderItemRepository.hasCompletedPurchase(customerId, productId)) {
      throw BusinessException.reviewNotEligible("You can review this product after a completed order");
    }
    if (reviewRepository.existsByUserIdAndProductId(customerId, productId)) {
      throw alreadyReviewed();
    }
    Review review =
        Review.builder()
            // Chỉ cần tham chiếu để gắn khoá ngoại: user chắc chắn tồn tại (đã qua token và điều kiện mua hàng).
            .user(userRepository.getReferenceById(customerId))
            .product(product)
            .rating(request.rating())
            .comment(request.comment().trim())
            .createdAt(timeProvider.now())
            .build();
    try {
      // Flush ngay: hai request đánh giá cùng lúc thì bên thua bật lỗi unique (user, product) tại đây.
      review = reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      throw alreadyReviewed();
    }
    // Bước 3: cộng vào điểm trung bình và số lượt đánh giá của product (một câu UPDATE).
    productService.addRating(productId, request.rating());
    return reviewMapper.toResponse(review);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<ReviewResponse> list(UUID productId, int page, int size) {
    findVisibleProduct(productId);
    return PageResponse.from(
        reviewRepository.findByProductId(productId, Pages.newestFirst(page, size)),
        reviewMapper::toResponse);
  }

  private Product findVisibleProduct(UUID productId) {
    return productRepository
        .findByIdAndActiveTrue(productId)
        .orElseThrow(() -> BusinessException.notFound("Product", productId));
  }

  private static BusinessException alreadyReviewed() {
    return BusinessException.reviewAlreadyExists("You already reviewed this product");
  }
}
