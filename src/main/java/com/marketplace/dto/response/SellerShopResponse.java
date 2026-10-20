package com.marketplace.dto.response;

import java.util.UUID;

/**
 * Trang shop công khai của một seller. Tên shop chính là tên của seller; không lộ email hay thông
 * tin riêng tư nào khác.
 *
 * @param productCount số product đang bán ({@code active = true}) của seller
 */
public record SellerShopResponse(UUID id, String name, long productCount) {}
