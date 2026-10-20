package com.marketplace.service;

import com.marketplace.dto.response.SellerShopResponse;
import java.util.UUID;

/** Trang shop công khai của seller. */
public interface SellerShopService {

  /**
   * Thông tin công khai của shop. Tên shop là tên của seller.
   *
   * @throws com.marketplace.common.exception.ResourceNotFoundException nếu không có người dùng đó,
   *     người dùng không phải seller, hoặc seller chưa {@code APPROVED} (không nói rõ là trường hợp nào)
   */
  SellerShopResponse getShop(UUID sellerId);
}
