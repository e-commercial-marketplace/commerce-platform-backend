package com.marketplace.websocket;

import java.util.Set;
import java.util.UUID;

/**
 * Sự kiện nội bộ: service báo "cần đẩy {@code event} tới các người dùng này". {@link ChatEventListener}
 * nhận nó sau khi transaction commit rồi gửi qua WebSocket.
 *
 * @param recipientIds người nhận (mọi kết nối đang mở của họ đều nhận)
 * @param event dữ liệu gửi đi, được chuyển thành JSON ({@link MessageCreatedEvent} hoặc
 *     {@link MessagesReadEvent})
 */
public record ChatPush(Set<UUID> recipientIds, Object event) {}
