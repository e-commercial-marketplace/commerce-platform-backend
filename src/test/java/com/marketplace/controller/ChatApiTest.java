package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.support.TestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** {@code /conversations/**} theo features/chat/API_CONTRACT.md (REST; phần đẩy WebSocket ở ChatWebSocketTest). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatApiTest {

  private static final String CONVERSATIONS = "/api/v1/conversations";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;

  private User customer;
  private User seller;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
  }

  // ---------- mở hội thoại ----------

  @Test
  void openingTheSamePairTwiceReturnsOneConversation() throws Exception {
    String first =
        id(
            open(customer, "{\"sellerId\":\"" + seller.getId() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.getId().toString()))
                .andExpect(jsonPath("$.data.lastMessageAt").doesNotExist()));

    // Lần hai (kể cả seller mở từ phía mình) trả lại đúng hội thoại cũ với 200.
    open(customer, "{\"sellerId\":\"" + seller.getId() + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(first));
    open(seller, "{\"customerId\":\"" + customer.getId() + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(first));
  }

  @Test
  void aCustomerCanOnlyOpenAConversationWithAnApprovedSeller() throws Exception {
    User pending = support.user(Role.SELLER, SellerStatus.PENDING);
    User otherCustomer = support.user(Role.CUSTOMER, null);

    open(customer, "{\"sellerId\":\"" + pending.getId() + "\"}").andExpect(status().isNotFound());
    open(customer, "{\"sellerId\":\"" + otherCustomer.getId() + "\"}").andExpect(status().isNotFound());
    open(customer, "{}").andExpect(status().isBadRequest());
    // Đơn không thuộc customer này (ở đây: không tồn tại) cũng là 404.
    open(customer, "{\"sellerId\":\"" + seller.getId() + "\",\"orderId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyCustomersAndSellersMayUseChat() throws Exception {
    User admin = support.user(Role.ADMIN, null);
    mvc.perform(get(CONVERSATIONS).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isForbidden());
    mvc.perform(get(CONVERSATIONS)).andExpect(status().isUnauthorized());
  }

  // ---------- gửi và đọc tin ----------

  @Test
  void resendingWithTheSameIdempotencyKeyStoresOneMessage() throws Exception {
    String conversation = openPair();
    String key = UUID.randomUUID().toString();

    String first = id(send(customer, conversation, key, "Còn hàng không shop?").andExpect(status().isCreated()));
    send(customer, conversation, key, "Còn hàng không shop?")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(first));

    history(seller, conversation)
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].senderId").value(customer.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].isRead").value(false));
  }

  @Test
  void aMessageNeedsAnIdempotencyKeyAndSomeContent() throws Exception {
    String conversation = openPair();

    support
        .send(mvc, post(CONVERSATIONS + "/" + conversation + "/messages"), customer, "{\"content\":\"hi\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    send(customer, conversation, UUID.randomUUID().toString(), "   ").andExpect(status().isBadRequest());
  }

  @Test
  void outsidersGet404ForAnotherPairsConversation() throws Exception {
    String conversation = openPair();
    User outsider = support.user(Role.CUSTOMER, null);

    history(outsider, conversation).andExpect(status().isNotFound());
    send(outsider, conversation, UUID.randomUUID().toString(), "hello").andExpect(status().isNotFound());
    mvc.perform(patch(CONVERSATIONS + "/" + conversation + "/read").header(HttpHeaders.AUTHORIZATION, support.bearer(outsider)))
        .andExpect(status().isNotFound());
  }

  @Test
  void historyIsNewestFirstAndTheListFollowsTheLatestMessage() throws Exception {
    String older = openPair();
    User otherSeller = support.user(Role.SELLER, SellerStatus.APPROVED);
    String newer = id(open(customer, "{\"sellerId\":\"" + otherSeller.getId() + "\"}"));

    send(customer, older, UUID.randomUUID().toString(), "tin 1");
    send(seller, older, UUID.randomUUID().toString(), "tin 2");

    history(customer, older)
        .andExpect(jsonPath("$.data.content[0].content").value("tin 2"))
        .andExpect(jsonPath("$.data.content[1].content").value("tin 1"));
    // Hội thoại có tin đứng trước hội thoại chưa có tin (dù hội thoại kia mở sau).
    mvc.perform(get(CONVERSATIONS).header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(older))
        .andExpect(jsonPath("$.data[0].lastMessageAt").exists())
        .andExpect(jsonPath("$.data[1].id").value(newer));
  }

  @Test
  void markingReadOnlyChangesTheOtherPersonsMessages() throws Exception {
    String conversation = openPair();
    send(customer, conversation, UUID.randomUUID().toString(), "từ customer");
    send(seller, conversation, UUID.randomUUID().toString(), "từ seller");

    mvc.perform(patch(CONVERSATIONS + "/" + conversation + "/read").header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isOk());

    // Mới nhất trước: [0] là tin của seller (vẫn chưa đọc), [1] là tin của customer (seller vừa đọc).
    history(customer, conversation)
        .andExpect(jsonPath("$.data.content[0].isRead").value(false))
        .andExpect(jsonPath("$.data.content[1].isRead").value(true));
  }

  // ---------- tiện ích ----------

  private String openPair() throws Exception {
    return id(open(customer, "{\"sellerId\":\"" + seller.getId() + "\"}"));
  }

  private ResultActions open(User who, String json) throws Exception {
    return support.send(mvc, post(CONVERSATIONS), who, json);
  }

  private ResultActions send(User who, String conversationId, String key, String content) throws Exception {
    return mvc.perform(
        post(CONVERSATIONS + "/" + conversationId + "/messages")
            .header(HttpHeaders.AUTHORIZATION, support.bearer(who))
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content("{\"content\":\"" + content + "\"}"));
  }

  private ResultActions history(User who, String conversationId) throws Exception {
    return mvc.perform(
        get(CONVERSATIONS + "/" + conversationId + "/messages").header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
