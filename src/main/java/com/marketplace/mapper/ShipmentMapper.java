package com.marketplace.mapper;

import com.marketplace.dto.response.ShipmentResponse;
import com.marketplace.dto.response.ShipmentResponse.ShipmentCarrierResponse;
import com.marketplace.dto.response.ShipmentResponse.ShipmentItemResponse;
import com.marketplace.entity.Carrier;
import com.marketplace.entity.Shipment;
import com.marketplace.entity.ShipmentOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Đổi shipment sang response; OrderMapper cũng dùng mapper này cho mảng {@code shipments} của order. */
@Mapper(componentModel = "spring")
public interface ShipmentMapper {

  @Mapping(target = "orderId", source = "order.id")
  @Mapping(target = "sellerId", source = "seller.id")
  @Mapping(target = "items", source = "shipmentItems")
  ShipmentResponse toResponse(Shipment shipment);

  // Tự giao thì carrier null, MapStruct trả null luôn.
  ShipmentCarrierResponse toCarrier(Carrier carrier);

  @Mapping(target = "orderItemId", source = "orderItem.id")
  @Mapping(target = "quantity", source = "orderItem.quantity")
  ShipmentItemResponse toItem(ShipmentOrderItem item);
}
