package com.marketplace.enums;

import com.marketplace.common.exception.BusinessException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

/** Các kiểu sắp xếp danh sách product; giá trị query param là {@link #param()}. */
public enum ProductSort {
  /** Khớp tên trước khớp mô tả; chỉ có nghĩa khi có từ khoá {@code q}. */
  RELEVANCE("relevance"),
  PRICE_ASC("price_asc"),
  PRICE_DESC("price_desc"),
  RATING_DESC("rating_desc");

  private final String param;

  ProductSort(String param) {
    this.param = param;
  }

  public String param() {
    return param;
  }

  /**
   * Chọn kiểu sắp xếp theo contract: không truyền {@code sort} thì mặc định là {@code relevance} khi
   * có {@code q}, ngược lại {@code rating_desc}; {@code relevance} mà không có {@code q} cũng quy về
   * {@code rating_desc}.
   *
   * @throws BusinessException 400 nếu giá trị {@code sort} không hợp lệ
   */
  public static ProductSort resolve(String requested, boolean hasQuery) {
    if (requested == null || requested.isBlank()) {
      return hasQuery ? RELEVANCE : RATING_DESC;
    }
    ProductSort sort =
        Arrays.stream(values())
            .filter(s -> s.param.equals(requested.trim()))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        "VALIDATION_ERROR",
                        "sort: must be one of relevance, price_asc, price_desc, rating_desc",
                        HttpStatus.BAD_REQUEST));
    return sort == RELEVANCE && !hasQuery ? RATING_DESC : sort;
  }
}
