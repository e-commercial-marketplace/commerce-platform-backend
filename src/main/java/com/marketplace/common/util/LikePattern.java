package com.marketplace.common.util;

import java.util.Locale;

/** Dựng pattern cho {@code LIKE} từ chuỗi người dùng nhập, để tìm kiếm không bị chèn ký tự đặc biệt. */
public final class LikePattern {

  /** Ký tự escape phải truyền cho {@code cb.like(..., pattern, LikePattern.ESCAPE)}. */
  public static final char ESCAPE = '\\';

  private LikePattern() {}

  /**
   * Pattern "chứa chuỗi" không phân biệt hoa thường: ký tự {@code % _ \} được coi là chữ thường chứ
   * không phải ký tự đại diện. So sánh với cột đã {@code lower(...)}.
   */
  public static String contains(String text) {
    String escaped =
        text.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
