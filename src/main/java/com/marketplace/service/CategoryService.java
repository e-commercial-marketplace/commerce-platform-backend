package com.marketplace.service;

import com.marketplace.dto.response.CategorySummaryResponse;
import java.util.List;

/** Nghiệp vụ category của catalog. */
public interface CategoryService {

  List<CategorySummaryResponse> listAll();
}
