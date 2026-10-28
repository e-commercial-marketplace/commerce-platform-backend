package com.marketplace.websocket;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Nhận {@link ChatPush} do service phát ra và đẩy qua WebSocket. {@code @TransactionalEventListener} mặc
 * định chạy SAU KHI transaction commit: transaction quay lui thì không đẩy gì cả.
 */
@Component
@RequiredArgsConstructor
public class ChatEventListener {

  private final ChatWebSocketHandler webSocketHandler;

  @TransactionalEventListener
  public void onChatPush(ChatPush push) {
    for (UUID recipientId : push.recipientIds()) {
      webSocketHandler.sendTo(recipientId, push.event());
    }
  }
}
