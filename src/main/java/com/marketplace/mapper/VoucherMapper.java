package com.marketplace.mapper;

import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.entity.Voucher;
import org.mapstruct.Mapper;

/** Đổi {@link Voucher} sang response; các trường cùng tên nên tự map. */
@Mapper(componentModel = "spring")
public interface VoucherMapper {

  VoucherResponse toResponse(Voucher voucher);
}
