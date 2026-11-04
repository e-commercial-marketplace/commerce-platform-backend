package com.marketplace.common.util;

import java.text.Normalizer;
import java.util.Locale;

/** Dựng pattern cho {@code LIKE} từ chuỗi người dùng nhập, để tìm kiếm không bị chèn ký tự đặc biệt. */
public final class LikePattern {

  /** Ký tự escape phải truyền cho {@code cb.like(..., pattern, LikePattern.ESCAPE)}. */
  public static final char ESCAPE = '\\';

  /** Nguyên âm có dấu và chữ đ, dạng chữ thường. Dùng cho hàm SQL {@code translate} và khi bỏ dấu câu tìm. */
  public static final String VIETNAMESE_ACCENTED =
      "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";

  /** Chữ không dấu ở đúng vị trí tương ứng với {@link #VIETNAMESE_ACCENTED}. */
  public static final String VIETNAMESE_PLAIN =
      "a".repeat(17) + "e".repeat(11) + "i".repeat(5) + "o".repeat(17) + "u".repeat(11) + "y".repeat(5) + "d";

  private LikePattern() {}

  /**
   * Pattern "chứa chuỗi" không phân biệt hoa thường: ký tự {@code % _ \} được coi là chữ thường chứ
   * không phải ký tự đại diện. So sánh với cột đã {@code lower(...)}.
   */
  public static String contains(String text) {
    return "%" + escape(text.toLowerCase(Locale.ROOT)) + "%";
  }

  /**
   * Như {@link #contains} nhưng bỏ cả dấu tiếng Việt: so sánh với cột đã qua {@code translate(lower(...))}.
   * Đưa về dạng NFC trước, vì một số bàn phím gửi chữ và dấu thành hai ký tự riêng.
   */
  public static String containsIgnoringAccents(String text) {
    String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    StringBuilder plain = new StringBuilder(lower.length());
    for (char c : lower.toCharArray()) {
      int index = VIETNAMESE_ACCENTED.indexOf(c);
      plain.append(index < 0 ? c : VIETNAMESE_PLAIN.charAt(index));
    }
    return "%" + escape(plain.toString()) + "%";
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
