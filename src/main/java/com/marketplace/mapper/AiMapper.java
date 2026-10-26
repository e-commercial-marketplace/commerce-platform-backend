package com.marketplace.mapper;

import com.marketplace.dto.response.AiChatSessionResponse;
import com.marketplace.dto.response.AiMessageResponse;
import com.marketplace.dto.response.AiProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.AiChatSession;
import com.marketplace.entity.AiMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Đổi dữ liệu của trợ lý AI sang response. */
@Mapper(componentModel = "spring")
public interface AiMapper {

  @Mapping(target = "userId", source = "user.id") // proxy LAZY: đọc id không cần query thêm
  AiChatSessionResponse toSession(AiChatSession session);

  @Mapping(target = "sessionId", source = "session.id")
  AiMessageResponse toMessage(AiMessage message);

  /** Chỉ giữ các field công khai cần cho thẻ sản phẩm (cùng tên nên tự map). */
  AiProductResponse toProduct(ProductSummaryResponse product);
}
