package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ReviewCreateRequest;
import com.marketplace.dto.response.ReviewResponse;
import java.util.UUID;

/** Đánh giá sản phẩm theo features/review/API_CONTRACT.md. */
public interface ReviewService {

  /** Customer có order COMPLETED chứa product này mới được đánh giá, và chỉ một lần. */
  ReviewResponse createReview(UUID customerId, UUID productId, ReviewCreateRequest request);

  /** Đánh giá công khai của một product đang bán, mới nhất trước. */
  PageResponse<ReviewResponse> listProductReviews(UUID productId, int page, int size);
}
