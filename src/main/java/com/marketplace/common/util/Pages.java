package com.marketplace.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Cách phân trang dùng chung cho các danh sách. */
public final class Pages {

  private Pages() {}

  /**
   * Mới tạo nhất trước ({@code createdAt} giảm dần), {@code id} làm khoá phụ để hai bản ghi cùng thời
   * điểm vẫn có thứ tự ổn định giữa các trang.
   */
  public static PageRequest newestFirst(int page, int size) {
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
  }
}
