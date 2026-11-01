package com.marketplace.common.util;

/** Đổi vector embedding sang dạng chữ mà pgvector hiểu, để truyền vào {@code CAST(? AS vector)}. */
public final class PgVector {

  private PgVector() {}

  /** Ví dụ {@code [0.1,0.2,0.3]}. */
  public static String toText(float[] vector) {
    StringBuilder text = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
