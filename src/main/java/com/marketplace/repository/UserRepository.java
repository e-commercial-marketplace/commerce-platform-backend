package com.marketplace.repository;

import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn user; email luôn được chuẩn hoá lowercase trước khi lưu và tìm. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  /** Dùng cho bootstrap admin: chỉ tạo admin đầu tiên khi chưa có admin nào. */
  boolean existsByRole(Role role);
}
