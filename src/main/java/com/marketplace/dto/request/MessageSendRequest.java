package com.marketplace.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /conversations/{id}/messages}. Người gửi lấy từ token.
 *
 * @param content nội dung, tối đa 2000 ký tự; chỉ được rỗng khi có {@code imageUrl} (service kiểm tra)
 * @param imageUrl đường dẫn ảnh tuỳ chọn, tối đa 2048 ký tự (tính năng này không upload ảnh)
 */
public record MessageSendRequest(@Size(max = 2000) String content, @Size(max = 2048) String imageUrl) {}
