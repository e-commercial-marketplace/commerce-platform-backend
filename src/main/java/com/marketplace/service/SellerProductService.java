package com.marketplace.service;

import com.marketplace.dto.request.ProductCreateRequest;
import com.marketplace.dto.request.ProductUpdateRequest;
import com.marketplace.dto.request.StockUpdateRequest;
import com.marketplace.dto.response.ProductResponse;
import java.util.UUID;

/**
 * Quản lý product của seller. Mọi hàm nhận {@code sellerId} lấy từ token (không bao giờ từ client)
 * và chỉ cho phép seller đã được duyệt thao tác trên product của chính mình.
 *
 * <p>Lỗi chung của các hàm: {@link com.marketplace.common.exception.ForbiddenException} (403) nếu seller
 * chưa {@code APPROVED} hoặc product thuộc seller khác; {@link
 * com.marketplace.common.exception.ResourceNotFoundException} (404) nếu không có product với id đó.
 */
public interface SellerProductService {

  /** Tạo product mới cho seller. Lỗi 400 nếu {@code categoryId} không tồn tại. */
  ProductResponse create(UUID sellerId, ProductCreateRequest request);

  /** Sửa một phần product; trường {@code null} trong request thì giữ nguyên. */
  ProductResponse update(UUID sellerId, UUID productId, ProductUpdateRequest request);

  /** Xoá mềm: đặt {@code active = false}, giữ lại dữ liệu để đơn hàng và review cũ vẫn tham chiếu được. */
  void delete(UUID sellerId, UUID productId);

  /** Đặt lại số lượng tồn kho. */
  ProductResponse updateStock(UUID sellerId, UUID productId, StockUpdateRequest request);
}
