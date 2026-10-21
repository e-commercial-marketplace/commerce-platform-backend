package com.marketplace.repository;

import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn user; email luôn được chuẩn hoá lowercase trước khi lưu và tìm. */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  /** Dùng cho bootstrap admin: chỉ tạo admin đầu tiên khi chưa có admin nào. */
  boolean existsByRole(Role role);

  /**
   * Đọc user và KHOÁ dòng đó tới hết transaction ({@code SELECT ... FOR UPDATE}). Dùng để các thao tác
   * sửa giỏ của cùng một customer chạy lần lượt: hai request "thêm vào giỏ" gần như cùng lúc sẽ không
   * cùng thấy "chưa có dòng" rồi cùng chèn, đụng ràng buộc unique.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);

  /** User theo id, không có thì 404 RESOURCE_NOT_FOUND. Dùng chung cho mọi service cần "user phải tồn tại". */
  default User getRequired(UUID id) {
    return findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
  }

  /** Như {@link #getRequired} nhưng khoá dòng user tới hết transaction (xem {@link #findByIdForUpdate}). */
  default User lockRequired(UUID id) {
    return findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
  }
}
