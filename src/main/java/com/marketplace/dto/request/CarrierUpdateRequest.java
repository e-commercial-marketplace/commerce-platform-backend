package com.marketplace.dto.request;

import com.marketplace.common.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /admin/carriers/{id}}. Trường {@code null} thì giữ nguyên.
 *
 * @param name tên mới; nếu gửi thì không được rỗng
 * @param active {@code false} để tắt (seller không chọn được nữa), {@code true} để bật lại
 */
public record CarrierUpdateRequest(@NullOrNotBlank @Size(max = 100) String name, Boolean active) {}
