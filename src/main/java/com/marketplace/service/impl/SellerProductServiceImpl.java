package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import static com.marketplace.repository.ProductSpecifications.activeIs;
import static com.marketplace.repository.ProductSpecifications.soldBy;
import static com.marketplace.repository.ProductSpecifications.textMatches;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ProductCreateRequest;
import com.marketplace.dto.request.ProductUpdateRequest;
import com.marketplace.dto.request.StockUpdateRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.mapper.ProductMapper;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.SellerProductService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Spring tạo và quản lý class nghiệp vụ này
@RequiredArgsConstructor // Lombok tạo constructor nhận 4 field final bên dưới
public class SellerProductServiceImpl implements SellerProductService {

  private final ProductRepository productRepository; // đọc/ghi product
  private final CategoryRepository categoryRepository; // tìm category theo id
  private final UserRepository userRepository; // tra trạng thái duyệt của seller trong DB
  private final ProductMapper productMapper; // đổi Product thành ProductResponse

  // Danh sách product của chính seller. Chỉ đọc nên khai báo readOnly.
  @Override
  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> list(
      UUID sellerId, String q, Boolean active, int page, int size) {
    // Bước 1: seller phải được duyệt.
    userRepository.getApprovedSeller(sellerId);
    // Bước 2: luôn bắt đầu bằng "chỉ product của seller này". sellerId lấy từ token nên không ai xem trộm
    // product của người khác được. Khác với danh sách công khai: KHÔNG lọc active, nên thấy cả product đã ẩn.
    Specification<Product> spec = soldBy(sellerId);
    // Bước 3: các bộ lọc tuỳ chọn.
    if (q != null && !q.isBlank()) {
      spec = spec.and(textMatches(q.trim())); // tìm trong tên và mô tả
    }
    if (active != null) {
      spec = spec.and(activeIs(active)); // true: đang bán, false: đã ẩn
    }
    // Bước 4: sắp theo tên rồi id (id làm tie-breaker để phân trang ổn định), đúng contract.
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    return PageResponse.from(productRepository.findAll(spec, pageable), productMapper::toSummary);
  }

  // Chi tiết một product của chính seller (kể cả đã ẩn). Chỉ đọc nên khai báo readOnly.
  @Override
  @Transactional(readOnly = true)
  public ProductResponse get(UUID sellerId, UUID productId) {
    userRepository.getApprovedSeller(sellerId); // seller đã duyệt chưa?
    // Không có product thì 404, của seller khác thì 403 (cùng quy tắc với sửa, xoá).
    return productMapper.toResponse(findOwnedProduct(sellerId, productId));
  }

  // @Transactional: mọi thay đổi trong hàm là một khối; hàm chạy xong thì Hibernate tự lưu các thay đổi.
  @Override
  @Transactional
  public ProductResponse create(UUID sellerId, ProductCreateRequest request) {
    // Bước 1: seller phải được duyệt (APPROVED), nếu không ném 403.
    User seller = userRepository.getApprovedSeller(sellerId);
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
    // Bước 5: product đang bán phải có ảnh.
    requireImageWhenActive(product);
    // Bước 6: lưu rồi trả về dạng response.
    return productMapper.toResponse(productRepository.save(product));
  }

  @Override
  @Transactional
  public ProductResponse update(UUID sellerId, UUID productId, ProductUpdateRequest request) {
    // Bước 1: seller đã duyệt chưa? Bước 2: product có tồn tại và có phải của seller này không?
    userRepository.getApprovedSeller(sellerId);
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
    // Bước 4: sau khi sửa, product đang bán vẫn phải có ảnh (lỗi thì cả giao dịch hoàn tác, không ghi gì).
    requireImageWhenActive(product);
    // Không cần gọi save(): product đang được Hibernate theo dõi, kết thúc giao dịch sẽ tự ghi thay đổi.
    return productMapper.toResponse(product);
  }

  @Override
  @Transactional
  public void delete(UUID sellerId, UUID productId) {
    userRepository.getApprovedSeller(sellerId);
    Product product = findOwnedProduct(sellerId, productId);
    // Xoá mềm: chỉ ẩn khỏi danh sách công khai, KHÔNG xoá dòng dữ liệu.
    product.setActive(false);
  }

  @Override
  @Transactional
  public ProductResponse updateStock(UUID sellerId, UUID productId, StockUpdateRequest request) {
    userRepository.getApprovedSeller(sellerId);
    Product product = findOwnedProduct(sellerId, productId);
    product.setStock(request.stock());
    return productMapper.toResponse(product);
  }

  /**
   * Seller phải có role SELLER và trạng thái APPROVED. Phải tra DB vì token chỉ chứa role, không chứa
   * trạng thái duyệt: seller bị khoá sau khi nhận token vẫn bị chặn ở đây.
   */

  /**
   * Tìm product và kiểm tra quyền sở hữu: không có product thì 404, có nhưng thuộc seller khác thì 403
   * (đúng contract; quy tắc FR-CAT-06: seller không được sửa product của seller khác).
   */
  private Product findOwnedProduct(UUID sellerId, UUID productId) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> BusinessException.notFound("Product", productId));
    if (!product.getSeller().getId().equals(sellerId)) {
      throw BusinessException.forbidden("Product belongs to another seller");
    }
    return product;
  }

  /** category phải tồn tại, nếu không là lỗi 400 VALIDATION_ERROR theo contract. */
  private Category findCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> BusinessException.validation("categoryId: unknown category"));
  }

  /**
   * Product đang bán phải có ảnh không rỗng (contract catalog; DB cũng có ràng buộc
   * ck_products_active_has_image). Kiểm tra ở đây để trả 400 rõ ràng thay vì để DB ném lỗi thành 500.
   * Product đã ẩn thì không cần ảnh.
   */
  private static void requireImageWhenActive(Product product) {
    if (product.isActive() && (product.getImageUrl() == null || product.getImageUrl().isBlank())) {
      throw BusinessException.validation("An active product must have an imageUrl");
    }
  }
}
