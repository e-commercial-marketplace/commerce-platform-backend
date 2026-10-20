package com.marketplace.repository;

import com.marketplace.common.util.LikePattern;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc user cho danh sách của admin. */
public final class UserSpecifications {

  private UserSpecifications() {}

  public static Specification<User> hasRole(Role role) {
    return (root, query, cb) -> cb.equal(root.get("role"), role);
  }

  /** Tìm không phân biệt hoa thường trong email hoặc tên. */
  public static Specification<User> emailOrNameContains(String text) {
    String pattern = LikePattern.contains(text);
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("email")), pattern, LikePattern.ESCAPE),
            cb.like(cb.lower(root.<String>get("fullName")), pattern, LikePattern.ESCAPE));
  }
}
