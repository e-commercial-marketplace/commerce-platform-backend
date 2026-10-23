package com.marketplace.mapper;

import com.marketplace.dto.response.CarrierResponse;
import com.marketplace.entity.Carrier;
import java.util.List;
import org.mapstruct.Mapper;

/** Đổi {@link Carrier} sang response; các trường cùng tên nên tự map. */
@Mapper(componentModel = "spring")
public interface CarrierMapper {

  CarrierResponse toResponse(Carrier carrier);

  List<CarrierResponse> toResponses(List<Carrier> carriers);
}
