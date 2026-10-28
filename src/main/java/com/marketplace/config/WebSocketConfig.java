package com.marketplace.config;

import com.marketplace.websocket.ChatWebSocketHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Đăng ký WebSocket {@code /ws/chat} (không có prefix {@code /api/v1}). Quyền vào kênh do
 * {@code SecurityConfig} kiểm tra ở handshake: chỉ CUSTOMER và SELLER có access token hợp lệ.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler chatWebSocketHandler;

  /** Cùng danh sách origin với CORS của REST; app mobile không gửi header Origin nên không bị ảnh hưởng. */
  @Value("${app.cors.allowed-origin-patterns:}")
  private List<String> allowedOriginPatterns;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    String[] origins =
        allowedOriginPatterns.stream().map(String::trim).filter(p -> !p.isEmpty()).toArray(String[]::new);
    registry.addHandler(chatWebSocketHandler, "/ws/chat").setAllowedOriginPatterns(origins);
  }
}
