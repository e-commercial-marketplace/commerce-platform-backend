package com.marketplace.repository;

import com.marketplace.entity.Carrier;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đơn vị vận chuyển; tên duy nhất không phân biệt hoa thường (giống category). */
public interface CarrierRepository extends JpaRepository<Carrier, UUID> {

  /** Admin xem tất cả, kể cả đơn vị đã tắt. */
  List<Carrier> findAllByOrderByNameAsc();

  /** Seller chỉ thấy đơn vị đang hoạt động. */
  List<Carrier> findByActiveTrueOrderByNameAsc();

  boolean existsByNameIgnoreCase(String name);

  /** Tên đã được dùng bởi đơn vị KHÁC (đổi thành chính tên của nó thì được phép). */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
