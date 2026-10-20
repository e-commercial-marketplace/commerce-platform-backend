package com.marketplace.common.util;

import java.util.Locale;

/** Quy tắc email dùng chung cho đăng ký và admin tạo user. */
public final class Emails {

  /** Thông báo khi email đã có người dùng. */
  public static final String TAKEN_MESSAGE = "Email is already registered";

  private Emails() {}

  /** Bỏ khoảng trắng hai đầu và đổi thành chữ thường để unique index không phân biệt hoa thường. */
  public static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
