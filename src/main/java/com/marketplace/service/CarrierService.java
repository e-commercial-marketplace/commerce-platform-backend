package com.marketplace.service;

import com.marketplace.dto.request.CarrierCreateRequest;
import com.marketplace.dto.request.CarrierUpdateRequest;
import com.marketplace.dto.response.CarrierResponse;
import java.util.List;
import java.util.UUID;

/** Đơn vị vận chuyển: admin quản lý, seller chọn khi giao qua nền tảng (FR-SHIP-14). */
public interface CarrierService {

  /** Admin: tất cả đơn vị, kể cả đã tắt, theo tên. */
  List<CarrierResponse> listAllCarriers();

  /** Seller đã duyệt: chỉ đơn vị đang hoạt động, theo tên. */
  List<CarrierResponse> listActiveCarriers(UUID sellerId);

  CarrierResponse createCarrier(CarrierCreateRequest request);

  /** Đổi tên và/hoặc bật tắt; trường null thì giữ nguyên. Không có xoá. */
  CarrierResponse updateCarrier(UUID id, CarrierUpdateRequest request);
}
