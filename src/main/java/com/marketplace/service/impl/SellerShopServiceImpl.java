package com.marketplace.service.impl;

import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.dto.response.SellerShopResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.SellerShopService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Spring tạo và quản lý class nghiệp vụ này
@RequiredArgsConstructor // Lombok tạo constructor nhận 2 field final bên dưới
public class SellerShopServiceImpl implements SellerShopService {

  private final UserRepository userRepository; // tìm seller theo id
  private final ProductRepository productRepository; // đếm product đang bán của seller

  @Override
  @Transactional(readOnly = true) // chỉ đọc nên khai báo readOnly
  public SellerShopResponse getShop(UUID sellerId) {
    // Bước 1: người dùng phải tồn tại, là SELLER và đã APPROVED. Mọi trường hợp còn lại đều là 404 giống nhau,
    // nên người ngoài không biết được "id này là customer", "seller chưa duyệt" hay "không tồn tại".
    User seller =
        userRepository
            .findById(sellerId)
            .filter(u -> u.getRole() == Role.SELLER && u.getSellerStatus() == SellerStatus.APPROVED)
            .orElseThrow(() -> new ResourceNotFoundException("Seller", sellerId));
    // Bước 2: đếm số product đang bán; chỉ trả tên (tên shop) và số lượng, không lộ email hay dữ liệu riêng tư.
    return new SellerShopResponse(
        seller.getId(), seller.getFullName(), productRepository.countBySellerIdAndActiveTrue(sellerId));
  }
}
