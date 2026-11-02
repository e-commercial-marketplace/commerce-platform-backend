package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.UploadSignatureRequest;
import com.marketplace.dto.response.UploadSignatureResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.UploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Upload ảnh theo features/upload/API_CONTRACT.md. Chỉ cần đăng nhập; quyền theo mục đích do service kiểm tra. */
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

  private final UploadService uploadService;

  @PostMapping("/signature")
  public ResponseEntity<ApiResponse<UploadSignatureResponse>> signature(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody UploadSignatureRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(uploadService.createSignature(me, request.purpose())));
  }
}
