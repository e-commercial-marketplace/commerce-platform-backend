package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ConversationCreateRequest;
import com.marketplace.dto.request.MessageSendRequest;
import com.marketplace.dto.response.ConversationResponse;
import com.marketplace.dto.response.MessageResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human chat theo features/chat/API_CONTRACT.md. URL thật là {@code /api/v1/conversations/**}, chỉ
 * CUSTOMER và SELLER (luật CHAT_PARTICIPANTS). Tin mới được đẩy qua WebSocket {@code /ws/chat}.
 */
@Validated
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ChatController {

  private final ChatService chatService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ConversationResponse>>> list(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(chatService.listConversations(me)));
  }

  /** Get-or-create: 201 khi vừa tạo, 200 khi cặp customer/seller đã có hội thoại. */
  @PostMapping
  public ResponseEntity<ApiResponse<ConversationResponse>> open(
      @AuthenticationPrincipal AuthUser me, @RequestBody ConversationCreateRequest request) {
    ChatService.OpenResult result = chatService.openConversation(me, request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.ok(result.conversation()));
  }

  @GetMapping("/{id}/messages")
  public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> messages(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(chatService.listMessages(me, id, page, size)));
  }

  /**
   * Header không đặt {@code required}: thiếu header thì service trả 400 VALIDATION_ERROR theo contract,
   * thay vì lỗi mặc định của Spring.
   */
  @PostMapping("/{id}/messages")
  public ResponseEntity<ApiResponse<MessageResponse>> send(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID id,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody MessageSendRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(chatService.sendMessage(me, id, idempotencyKey, request)));
  }

  @PatchMapping("/{id}/read")
  public ResponseEntity<ApiResponse<Void>> markRead(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    chatService.markRead(me, id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
