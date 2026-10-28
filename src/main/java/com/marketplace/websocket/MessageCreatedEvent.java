package com.marketplace.websocket;

import com.marketplace.dto.response.MessageResponse;

/** JSON gửi qua WebSocket khi có tin mới: {@code {"type":"MESSAGE_CREATED","message":{...}}}. */
public record MessageCreatedEvent(String type, MessageResponse message) {

  public MessageCreatedEvent(MessageResponse message) {
    this("MESSAGE_CREATED", message);
  }
}
