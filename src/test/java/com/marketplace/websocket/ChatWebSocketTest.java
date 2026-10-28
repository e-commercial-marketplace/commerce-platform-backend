package com.marketplace.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.support.TestSupport;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Kênh {@code /ws/chat} với server HTTP thật (cổng ngẫu nhiên): người nhận đang kết nối thấy sự kiện ngay
 * sau khi REST lưu xong; người ngoài không thấy gì; không có token thì không kết nối được.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketTest {

  @LocalServerPort private int port;
  @Autowired private TestSupport support;
  @Autowired private TestRestTemplate rest;

  private User customer;
  private User seller;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
  }

  @Test
  void theRecipientIsPushedTheNewMessageAndAnOutsiderIsNot() throws Exception {
    BlockingQueue<String> sellerInbox = new LinkedBlockingQueue<>();
    BlockingQueue<String> outsiderInbox = new LinkedBlockingQueue<>();
    WebSocketSession sellerSocket = connect(seller, sellerInbox);
    WebSocketSession outsiderSocket = connect(support.user(Role.CUSTOMER, null), outsiderInbox);

    String conversationId = openConversation();
    call(HttpMethod.POST, "/conversations/" + conversationId + "/messages", customer, "{\"content\":\"Còn hàng không?\"}");

    String event = sellerInbox.poll(5, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("MESSAGE_CREATED");
    assertThat((String) JsonPath.read(event, "$.message.conversationId")).isEqualTo(conversationId);
    assertThat((String) JsonPath.read(event, "$.message.content")).isEqualTo("Còn hàng không?");
    assertThat(outsiderInbox.poll(1, TimeUnit.SECONDS)).isNull();

    sellerSocket.close();
    outsiderSocket.close();
  }

  @Test
  void readingTellsTheSenderThatTheMessagesWereRead() throws Exception {
    String conversationId = openConversation();
    call(HttpMethod.POST, "/conversations/" + conversationId + "/messages", customer, "{\"content\":\"hi\"}");

    BlockingQueue<String> customerInbox = new LinkedBlockingQueue<>();
    WebSocketSession customerSocket = connect(customer, customerInbox);
    call(HttpMethod.PATCH, "/conversations/" + conversationId + "/read", seller, null);

    String event = customerInbox.poll(5, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("MESSAGES_READ");
    assertThat((String) JsonPath.read(event, "$.readerId")).isEqualTo(seller.getId().toString());

    customerSocket.close();
  }

  @Test
  void aHandshakeWithoutATokenIsRejected() {
    StandardWebSocketClient client = new StandardWebSocketClient();
    assertThatThrownBy(
            () ->
                client
                    .execute(new TextWebSocketHandler(), new WebSocketHttpHeaders(), socketUri())
                    .get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  // ---------- tiện ích ----------

  /** Mở WebSocket bằng token của {@code who}; mọi tin server đẩy tới được cho vào {@code inbox}. */
  private WebSocketSession connect(User who, BlockingQueue<String> inbox) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, support.bearer(who));
    TextWebSocketHandler collector =
        new TextWebSocketHandler() {
          @Override
          protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            inbox.add(message.getPayload());
          }
        };
    return new StandardWebSocketClient().execute(collector, headers, socketUri()).get(5, TimeUnit.SECONDS);
  }

  private String openConversation() {
    String body = call(HttpMethod.POST, "/conversations", customer, "{\"sellerId\":\"" + seller.getId() + "\"}");
    return JsonPath.read(body, "$.data.id");
  }

  /** Gọi REST API thật với token của {@code who}; tin nhắn luôn kèm một Idempotency-Key mới. */
  private String call(HttpMethod method, String path, User who, String json) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, support.bearer(who));
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", UUID.randomUUID().toString());
    return rest.exchange("/api/v1" + path, method, new HttpEntity<>(json, headers), String.class).getBody();
  }

  private URI socketUri() {
    return URI.create("ws://localhost:" + port + "/ws/chat");
  }
}
