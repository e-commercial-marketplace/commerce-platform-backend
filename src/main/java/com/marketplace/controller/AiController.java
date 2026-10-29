package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.util.ClientIp;
import com.marketplace.dto.request.AiAskRequest;
import com.marketplace.dto.response.AiChatSessionResponse;
import com.marketplace.dto.response.AiMessageResponse;
import com.marketplace.dto.response.AiProductSearchResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trợ lý AI theo features/ai/API_CONTRACT.md. */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

  private final AiService aiService;

  /** Công khai: guest thì {@code me} là {@code null}; có token thì service kiểm tra phải là customer. */
  @PostMapping("/ask")
  public ResponseEntity<ApiResponse<AiProductSearchResponse>> ask(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody AiAskRequest request, HttpServletRequest http) {
    return ResponseEntity.ok(ApiResponse.ok(aiService.answerQuestion(me, request, ClientIp.of(http))));
  }

  @GetMapping("/sessions")
  public ResponseEntity<ApiResponse<List<AiChatSessionResponse>>> sessions(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(aiService.listSessions(me.id())));
  }

  @GetMapping("/sessions/{id}/messages")
  public ResponseEntity<ApiResponse<List<AiMessageResponse>>> messages(
      @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(aiService.listSessionMessages(me.id(), id)));
  }
}
