package com.marketplace.service;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Product;
import java.util.UUID;

/** Nghiệp vụ product của catalog. */
public interface ProductService {

  /**
   * Duyệt/tìm product đang bán.
   *
   * @throws com.marketplace.common.exception.BusinessException 400 nếu khoảng giá hoặc {@code sort} sai
   */
  PageResponse<ProductSummaryResponse> search(ProductSearchRequest request);

  /**
   * @throws com.marketplace.common.exception.BusinessException nếu không có product đang bán với
   *     {@code id}
   */
  ProductResponse getActiveProduct(UUID id);

  /** Product đã ngừng bán thì 409 PRODUCT_INACTIVE; dùng khi thêm vào giỏ và khi checkout. */
  void ensureActive(Product product);
}
