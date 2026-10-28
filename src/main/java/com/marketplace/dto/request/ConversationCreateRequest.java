package com.marketplace.dto.request;

import java.util.UUID;

/**
 * Body của {@code POST /conversations}. Phía người gọi lấy từ token; body chỉ nêu người còn lại.
 *
 * @param sellerId bắt buộc khi người gọi là CUSTOMER: seller đã APPROVED
 * @param customerId bắt buộc khi người gọi là SELLER: một CUSTOMER
 * @param productId ngữ cảnh sản phẩm, tuỳ chọn
 * @param orderId ngữ cảnh đơn hàng, tuỳ chọn; phải là đơn của customer trong cặp
 */
public record ConversationCreateRequest(UUID sellerId, UUID customerId, UUID productId, UUID orderId) {}
