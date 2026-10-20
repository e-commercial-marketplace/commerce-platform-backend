package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.exception.ForbiddenException;
import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.dto.request.ProductCreateRequest;
import com.marketplace.dto.request.ProductUpdateRequest;
import com.marketplace.dto.request.StockUpdateRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.mapper.ProductMapper;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.SellerProductService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Spring tạo và quản lý class nghiệp vụ này
@RequiredArgsConstructor // Lombok tạo constructor nhận 4 field final bên dưới
public class SellerProductServiceImpl implements SellerProductService {

  private final ProductRepository productRepository; // đọc/ghi product
  private final CategoryRepository categoryRepository; // tìm category theo id
  private final UserRepository userRepository; // tra trạng thái duyệt của seller trong DB
  private final ProductMapper productMapper; // đổi Product thành ProductResponse

  // @Transactional: mọi thay đổi trong hàm là một khối; hàm chạy xong thì Hibernate tự lưu các thay đổi.
  @Override
  @Transactional
  public ProductResponse create(UUID sellerId, ProductCreateRequest request) {
    // Bước 1: seller phải được duyệt (APPROVED), nếu không ném 403.
    User seller = requireApprovedSeller(sellerId);
    // Bước 2: category phải tồn tại, nếu không ném 400.
    Category category = findCategory(request.categoryId());
    // Bước 3: tạo product. sellerId lấy từ token chứ không nhận từ client, nên không ai tạo hộ người khác được.
    Product product =
        Product.create(
            request.name().trim(),
            request.description(),
            request.imageUrl(),
            request.price(),
            request.stock(),
            category,
            seller);
    // Bước 4: mặc định product đang bán; chỉ khi client gửi rõ "active": false mới tạo ở trạng thái ẩn.
    if (Boolean.FALSE.equals(request.active())) {
      product.setActive(false);
    }
    // Bước 5: lưu rồi trả về dạng response.
    return productMapper.toResponse(productRepository.save(product));
  }

  @Override
  @Transactional
  public ProductResponse update(UUID sellerId, UUID productId, ProductUpdateRequest request) {
    // Bước 1: seller đã duyệt chưa? Bước 2: product có tồn tại và có phải của seller này không?
    requireApprovedSeller(sellerId);
    Product product = findOwnedProduct(sellerId, productId);
    // Bước 3: chỉ đổi những trường client có gửi (khác null); trường không gửi giữ nguyên.
    if (request.name() != null) {
      product.setName(request.name().trim());
    }
    if (request.description() != null) {
      product.setDescription(request.description());
    }
    if (request.imageUrl() != null) {
      product.setImageUrl(request.imageUrl());
    }
    if (request.price() != null) {
      product.setPrice(request.price());
    }
    if (request.categoryId() != null) {
      product.setCategory(findCategory(request.categoryId()));
    }
    if (request.active() != null) {
      product.setActive(request.active()); // đặt true để bán lại một product đã ẩn
    }
    // Không cần gọi save(): product đang được Hibernate theo dõi, kết thúc giao dịch sẽ tự ghi thay đổi.
    return productMapper.toResponse(product);
  }

  @Override
  @Transactional
  public void delete(UUID sellerId, UUID productId) {
    requireApprovedSeller(sellerId);
    Product product = findOwnedProduct(sellerId, productId);
    // Xoá mềm: chỉ ẩn khỏi danh sách công khai, KHÔNG xoá dòng dữ liệu.
    product.setActive(false);
  }

  @Override
  @Transactional
  public ProductResponse updateStock(UUID sellerId, UUID productId, StockUpdateRequest request) {
    requireApprovedSeller(sellerId);
    Product product = findOwnedProduct(sellerId, productId);
    product.setStock(request.stock());
    return productMapper.toResponse(product);
  }

  /**
   * Seller phải có role SELLER và trạng thái APPROVED. Phải tra DB vì token chỉ chứa role, không chứa
   * trạng thái duyệt: seller bị khoá sau khi nhận token vẫn bị chặn ở đây.
   */
  private User requireApprovedSeller(UUID sellerId) {
    User user =
        userRepository
            .findById(sellerId)
            .orElseThrow(() -> new ForbiddenException("Seller account not found"));
    if (user.getRole() != Role.SELLER || user.getSellerStatus() != SellerStatus.APPROVED) {
      throw new ForbiddenException("Seller is not approved");
    }
    return user;
  }

  /**
   * Tìm product và kiểm tra quyền sở hữu: không có product thì 404, có nhưng thuộc seller khác thì 403
   * (đúng contract; quy tắc FR-CAT-06: seller không được sửa product của seller khác).
   */
  private Product findOwnedProduct(UUID sellerId, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    if (!product.getSeller().getId().equals(sellerId)) {
      throw new ForbiddenException("Product belongs to another seller");
    }
    return product;
  }

  /** category phải tồn tại, nếu không là lỗi 400 VALIDATION_ERROR theo contract. */
  private Category findCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(
            () ->
                new BusinessException(
                    "VALIDATION_ERROR", "categoryId: unknown category", HttpStatus.BAD_REQUEST));
  }
}
