package com.marketplace.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.security.AuthUser;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Kênh WebSocket {@code /ws/chat} chỉ đẩy (features/chat/API_CONTRACT.md): server gửi sự kiện, client không
 * gửi gì (tin client gửi lên bị bỏ qua; gửi tin đi bằng REST).
 *
 * <p>Người dùng của kết nối lấy từ handshake: handshake là một request HTTP đi qua security chain có sẵn,
 * {@code JwtAuthenticationFilter} đã đặt {@link AuthUser}, nên {@code session.getPrincipal()} là người đó.
 *
 * <p>shortcut: danh sách kết nối nằm trong RAM của một instance, đủ khi Render chạy một instance; chạy nhiều
 * instance thì cần broker dùng chung (ví dụ Redis pub/sub) để đẩy tới kết nối ở instance khác.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

  /** Thời gian tối đa (ms) chờ gửi một tin, và số byte tối đa được xếp hàng cho một kết nối chậm. */
  private static final int SEND_TIME_LIMIT_MS = 5_000;
  private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

  /** Tên attribute giữ bản "an toàn khi nhiều thread cùng gửi" của kết nối. */
  private static final String SAFE_SESSION = "safeSession";

  private final ObjectMapper objectMapper;

  /** userId → các kết nối đang mở của người đó (một người có thể mở app trên nhiều thiết bị). */
  private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    UUID userId = userIdOf(session);
    // WebSocketSession không an toàn khi hai thread cùng gửi; decorator xếp hàng các lần gửi giúp ta.
    WebSocketSession safeSession =
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
    // Cất bản decorator vào attributes để lúc đóng kết nối tìm lại đúng đối tượng mà xoá.
    session.getAttributes().put(SAFE_SESSION, safeSession);
    sessionsByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(safeSession);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID userId = userIdOf(session);
    Set<WebSocketSession> sessions = sessionsByUser.get(userId);
    if (sessions == null) {
      return;
    }
    sessions.remove(session.getAttributes().get(SAFE_SESSION));
    if (sessions.isEmpty()) {
      sessionsByUser.remove(userId, sessions);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // Kênh chỉ đẩy: bỏ qua mọi tin client gửi lên.
  }

  /** Gửi {@code event} (chuyển thành JSON) tới mọi kết nối đang mở của {@code userId}. */
  public void sendTo(UUID userId, Object event) {
    Set<WebSocketSession> sessions = sessionsByUser.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return; // người đó không online: họ sẽ tải lịch sử qua REST khi mở app
    }
    TextMessage frame;
    try {
      frame = new TextMessage(objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize chat event: {}", e.toString());
      return;
    }
    for (WebSocketSession session : sessions) {
      try {
        if (session.isOpen()) {
          session.sendMessage(frame);
        }
      } catch (IOException | RuntimeException e) {
        // Một kết nối hỏng không được làm hỏng việc gửi tới các kết nối khác.
        log.warn("Could not push chat event to user {}: {}", userId, e.toString());
      }
    }
  }

  private static UUID userIdOf(WebSocketSession session) {
    Authentication authentication = (Authentication) session.getPrincipal();
    return ((AuthUser) authentication.getPrincipal()).id();
  }
}
