package com.marketplace.repository;

import com.marketplace.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn order; mọi truy vấn của customer đều lọc theo customerId lấy từ token. */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  /** Các order của một customer; thứ tự do Pageable quyết định (mới nhất trước). */
  Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

  /**
   * Một order của đúng customer này. Order của người khác cũng ra rỗng, nên controller trả 404 giống
   * hệt order không tồn tại (không lộ việc id đó có thật). Nạp sẵn các dòng hàng.
   */
  @EntityGraph(attributePaths = "items")
  Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);
}
