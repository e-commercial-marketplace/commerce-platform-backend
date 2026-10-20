package com.marketplace.repository;

import com.marketplace.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /** Danh sách category cho màn hình duyệt, sắp theo tên để thứ tự ổn định. */
  List<Category> findAllByOrderByNameAsc();
}
