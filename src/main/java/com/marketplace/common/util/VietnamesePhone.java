package com.marketplace.common.util;

/**
 * Số di động Việt Nam theo contract user: {@code 0} hoặc {@code +84}, rồi 9 chữ số mà chữ số đầu là
 * 3, 5, 7, 8 hoặc 9. Ví dụ {@code 0912345678} hoặc {@code +84912345678}. Lưu thống nhất dạng {@code 0...}.
 */
public final class VietnamesePhone {

  /**
   * Biểu thức dùng cho {@code @Pattern} ở request. Chấp nhận thêm chuỗi rỗng, vì trong request cập nhật
   * chuỗi rỗng nghĩa là "xoá số điện thoại".
   */
  public static final String PATTERN = "^$|^(0|\\+84)[35789]\\d{8}$";

  private VietnamesePhone() {}

  /**
   * Đưa về dạng lưu trữ: bỏ khoảng trắng hai đầu, {@code +84} đổi thành {@code 0}, chuỗi rỗng thành
   * {@code null} (không có số). Giả định giá trị đã qua kiểm tra {@link #PATTERN}.
   */
  public static String normalize(String raw) {
    String value = raw.trim();
    if (value.isEmpty()) {
      return null;
    }
    return value.startsWith("+84") ? "0" + value.substring(3) : value;
  }
}
