package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ShipmentCreateRequest;
import com.marketplace.dto.request.ShipmentStatusUpdateRequest;
import com.marketplace.dto.response.CarrierResponse;
import com.marketplace.dto.response.ShipmentResponse;
import com.marketplace.enums.ShipmentStatus;
import com.marketplace.security.AuthUser;
import com.marketplace.service.CarrierService;
import com.marketplace.service.ShipmentService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller giao hàng: xem đơn vị vận chuyển đang hoạt động, tạo shipment và đổi trạng thái. URL thật là
 * {@code /api/v1/seller/...}; chỉ role SELLER (luật SELLER_ONLY), trạng thái APPROVED kiểm trong service.
 */
@Validated
@RestController
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerShipmentController {

  private final ShipmentService shipmentService;
  private final CarrierService carrierService;

  @GetMapping("/carriers")
  public ResponseEntity<ApiResponse<List<CarrierResponse>>> carriers(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(carrierService.listActive(me.id())));
  }

  @PostMapping("/shipments")
  public ResponseEntity<ApiResponse<ShipmentResponse>> create(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ShipmentCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(shipmentService.create(me.id(), request)));
  }

  @GetMapping("/shipments")
  public ResponseEntity<ApiResponse<PageResponse<ShipmentResponse>>> list(
      @AuthenticationPrincipal AuthUser me,
      @RequestParam(required = false) ShipmentStatus status,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(shipmentService.list(me.id(), status, orderId, page, size)));
  }

  @PatchMapping("/shipments/{id}/status")
  public ResponseEntity<ApiResponse<ShipmentResponse>> updateStatus(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID id,
      @Valid @RequestBody ShipmentStatusUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(shipmentService.updateStatus(me.id(), id, request)));
  }
}
