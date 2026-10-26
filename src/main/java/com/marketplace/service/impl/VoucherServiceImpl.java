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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  private final EntityManager entityManager;
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
    // Voucher mới, chưa dùng lần nào.
    Voucher voucher =
        Voucher.builder()
            .code(code)
            .description(request.description())
            .discountPercent(request.discountPercent())
            .validFrom(request.validFrom())
            .validTo(request.validTo())
            .usageLimit(request.usageLimit())
            .build();
    checkRules(voucher);
    try {
      // Khoá chính tự đặt (code) nên save() của Spring Data sẽ merge, có thể ghi đè voucher vừa được tạo
      // cùng mã. persist() luôn INSERT; flush ngay để hai admin tạo cùng mã thì bên thua bật lỗi khoá chính.
      entityManager.persist(voucher);
      entityManager.flush();
      return voucherMapper.toResponse(voucher);
    } catch (DataIntegrityViolationException | PersistenceException e) {
      // Gọi thẳng EntityManager (không qua repository) nên Spring không dịch lỗi: trùng khoá chính đến dạng
      // PersistenceException của JPA; bắt cả hai để trùng mã luôn là 400.
      throw duplicateCode();
    }
  }

  @Override
  @Transactional
  public VoucherResponse update(String code, VoucherUpdateRequest request) {
    Voucher voucher = find(code);
    // Trường null thì giữ nguyên; code không đổi được.
    if (request.description() != null) voucher.setDescription(request.description());
    Integer percent = request.discountPercent();
    if (percent != null && percent != voucher.getDiscountPercent()) {
      // Order đã dùng voucher tính lại giảm giá theo % hiện tại khi huỷ dòng, nên đổi % sẽ đổi tổng của chúng.
      if (voucher.getTimesUsed() > 0) {
        throw BusinessException.validation("discountPercent cannot change after the voucher has been used");
      }
      voucher.setDiscountPercent(percent);
    }
    if (request.validFrom() != null) voucher.setValidFrom(request.validFrom());
    if (request.validTo() != null) voucher.setValidTo(request.validTo());
    if (request.usageLimit() != null) voucher.setUsageLimit(request.usageLimit());
    checkRules(voucher);
    return voucherMapper.toResponse(voucher);
  }

  @Override
  @Transactional
  public VoucherResponse expire(String code) {
    Voucher voucher = find(code);
    // Hết hạn ngay (không xoá, để giữ lịch sử). Voucher chưa bắt đầu thì lùi validFrom về ngay trước now,
    // vì DB bắt buộc validFrom < validTo. Cắt xuống micro giây: DB chỉ lưu tới micro giây và làm tròn, nếu
    // để nano giây thì validTo lưu có thể lớn hơn now một chút và voucher còn dùng được ngay sau khi hết hạn.
    Instant now = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
    if (!voucher.getValidFrom().isBefore(now)) {
      voucher.setValidFrom(now.minusSeconds(1));
    }
    voucher.setValidTo(now);
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
    // Hết hạn hoặc chưa tới ngày bắt đầu.
    if (now.isBefore(voucher.getValidFrom()) || !now.isBefore(voucher.getValidTo())) {
      throw BusinessException.voucherExpired("Voucher is not valid at this time");
    }
    if (voucher.getTimesUsed() >= voucher.getUsageLimit()) {
      throw BusinessException.voucherExhausted("Voucher has no use left");
    }
  }

  // Cùng các luật với ràng buộc CHECK của bảng vouchers (V13), báo 400 trước khi DB ném lỗi thành 500.
  private static void checkRules(Voucher voucher) {
    if (!voucher.getValidFrom().isBefore(voucher.getValidTo())) {
      throw BusinessException.validation("validFrom must be before validTo");
    }
    if (voucher.getUsageLimit() < voucher.getTimesUsed()) {
      throw BusinessException.validation(
          "usageLimit cannot be below timesUsed (" + voucher.getTimesUsed() + ")");
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
