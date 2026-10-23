package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.CarrierCreateRequest;
import com.marketplace.dto.request.CarrierUpdateRequest;
import com.marketplace.dto.response.CarrierResponse;
import com.marketplace.service.CarrierService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin quản lý đơn vị vận chuyển. URL thật là {@code /api/v1/admin/carriers/...}; role ADMIN được chặn
 * ở {@code SecurityConfig} (luật {@code /api/v1/admin/**}). Không có xoá: tắt bằng {@code active=false}.
 */
@RestController
@RequestMapping("/admin/carriers")
@RequiredArgsConstructor
public class AdminCarrierController {

  private final CarrierService carrierService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CarrierResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(carrierService.listAll()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CarrierResponse>> create(
      @Valid @RequestBody CarrierCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(carrierService.create(request)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<CarrierResponse>> update(
      @PathVariable UUID id, @Valid @RequestBody CarrierUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(carrierService.update(id, request)));
  }
}
