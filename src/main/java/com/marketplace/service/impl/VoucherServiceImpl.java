package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.VoucherCreateRequest;
import com.marketplace.dto.request.VoucherUpdateRequest;
import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.entity.Voucher;
import com.marketplace.mapper.VoucherMapper;
import com.marketplace.repository.VoucherRepository;
import com.marketplace.service.VoucherService;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Voucher của nền tảng; mã luôn được chuẩn hoá (bỏ khoảng trắng, viết hoa) trước khi tìm hoặc lưu. */
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

  private final VoucherRepository voucherRepository;
  private final VoucherMapper voucherMapper;
  private final TimeProvider timeProvider;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<VoucherResponse> list(int page, int size) {
    // Voucher bắt đầu gần đây nhất trước; code làm khoá phụ cho thứ tự ổn định giữa các trang.
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "validFrom").and(Sort.by("code")));
    return PageResponse.from(voucherRepository.findAll(pageable), voucherMapper::toResponse);
  }

  @Override
  @Transactional
  public VoucherResponse create(VoucherCreateRequest request) {
    String code = normalize(request.code());
    if (voucherRepository.existsById(code)) {
      throw duplicateCode();
    }
    Voucher voucher =
        Voucher.create(
            code,
            request.description(),
            request.discountPercent(),
            request.validFrom(),
            request.validTo(),
            request.usageLimit());
    try {
      // Flush ngay: hai admin tạo cùng mã một lúc thì bên thua bật lỗi khoá chính tại đây.
      return voucherMapper.toResponse(voucherRepository.saveAndFlush(voucher));
    } catch (DataIntegrityViolationException e) {
      throw duplicateCode();
    }
  }

  @Override
  @Transactional
  public VoucherResponse update(String code, VoucherUpdateRequest request) {
    Voucher voucher = find(code);
    voucher.update(
        request.description(),
        request.discountPercent(),
        request.validFrom(),
        request.validTo(),
        request.usageLimit());
    return voucherMapper.toResponse(voucher);
  }

  @Override
  @Transactional
  public VoucherResponse expire(String code) {
    Voucher voucher = find(code);
    voucher.expire(timeProvider.now());
    return voucherMapper.toResponse(voucher);
  }

  @Override
  @Transactional(readOnly = true)
  public VoucherResponse validate(String code) {
    Voucher voucher = find(code);
    checkUsable(voucher, timeProvider.now());
    return voucherMapper.toResponse(voucher);
  }

  @Override
  @Transactional
  public Voucher consume(String code, Instant now) {
    String normalized = normalize(code);
    // Trừ lượt bằng một câu UPDATE có điều kiện; được thì đọc voucher (đã có lượt mới) để gắn vào order.
    if (voucherRepository.consume(normalized, now) == 1) {
      return find(normalized);
    }
    // Không trừ được: tìm lý do để báo đúng lỗi (không có, hết hạn, hay hết lượt).
    Voucher voucher = find(normalized);
    checkUsable(voucher, now);
    throw BusinessException.voucherExhausted("Voucher has no use left"); // vừa hết lượt do request khác
  }

  private static void checkUsable(Voucher voucher, Instant now) {
    if (voucher.isOutsideWindow(now)) {
      throw BusinessException.voucherExpired("Voucher is not valid at this time");
    }
    if (voucher.isExhausted()) {
      throw BusinessException.voucherExhausted("Voucher has no use left");
    }
  }

  private Voucher find(String code) {
    String normalized = normalize(code);
    return voucherRepository
        .findById(normalized)
        .orElseThrow(() -> BusinessException.notFound("Voucher", normalized));
  }

  /** Bỏ khoảng trắng hai đầu và viết hoa, để "sale10" và "SALE10" là một mã. */
  private static String normalize(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private static BusinessException duplicateCode() {
    return BusinessException.validation("Voucher code already exists");
  }
}
