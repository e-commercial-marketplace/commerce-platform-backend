package com.marketplace.websocket;

import java.util.UUID;

/**
 * JSON gửi qua WebSocket khi một người đã đọc tin:
 * {@code {"type":"MESSAGES_READ","conversationId":"...","readerId":"..."}}.
 */
public record MessagesReadEvent(String type, UUID conversationId, UUID readerId) {

  public MessagesReadEvent(UUID conversationId, UUID readerId) {
    this("MESSAGES_READ", conversationId, readerId);
  }
}
