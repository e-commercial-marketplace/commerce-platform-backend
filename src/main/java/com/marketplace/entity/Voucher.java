package com.marketplace.entity;

import com.marketplace.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Voucher của nền tảng, khoá chính là {@code code} (đã chuẩn hoá viết hoa). Dùng được khi
 * {@code validFrom <= now < validTo} và {@code timesUsed < usageLimit}. Lượt dùng được trừ bằng một câu
 * UPDATE có điều kiện ở {@code VoucherRepository.consume}, không qua entity, để nhiều checkout cùng lúc
 * không vượt {@code usageLimit}.
 */
@Entity
@Table(name = "vouchers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Voucher implements Persistable<String> {

  @Id
  @Column(name = "code", length = 64)
  private String code;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "discount_percent", nullable = false)
  private int discountPercent;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_to", nullable = false)
  private Instant validTo;

  @Column(name = "usage_limit", nullable = false)
  private int usageLimit;

  @Column(name = "times_used", nullable = false)
  private int timesUsed;

  // Khoá chính tự đặt (code) nên Spring Data không tự biết bản ghi mới hay cũ và sẽ merge (có thể ghi đè
  // voucher trùng mã). Đánh dấu bản vừa create là mới để save() luôn INSERT; trùng mã thì bật lỗi khoá chính.
  @Transient private boolean newEntity;

  @Override
  public String getId() {
    return code;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }

  /** Voucher mới, chưa dùng lần nào. {@code code} phải đã được chuẩn hoá (viết hoa, bỏ khoảng trắng). */
  public static Voucher create(
      String code,
      String description,
      int discountPercent,
      Instant validFrom,
      Instant validTo,
      int usageLimit) {
    Voucher voucher = new Voucher();
    voucher.code = code;
    voucher.description = description;
    voucher.discountPercent = discountPercent;
    voucher.validFrom = validFrom;
    voucher.validTo = validTo;
    voucher.usageLimit = usageLimit;
    voucher.newEntity = true;
    voucher.checkRules();
    return voucher;
  }

  /** Admin sửa; tham số {@code null} thì giữ nguyên. {@code code} không đổi được. */
  public void update(
      String description, Integer discountPercent, Instant validFrom, Instant validTo, Integer usageLimit) {
    if (description != null) this.description = description;
    if (discountPercent != null && discountPercent != this.discountPercent) {
      // Order đã dùng voucher tính lại giảm giá theo % hiện tại khi huỷ dòng, nên đổi % sẽ đổi tổng của chúng.
      if (timesUsed > 0) {
        throw BusinessException.validation("discountPercent cannot change after the voucher has been used");
      }
      this.discountPercent = discountPercent;
    }
    if (validFrom != null) this.validFrom = validFrom;
    if (validTo != null) this.validTo = validTo;
    if (usageLimit != null) this.usageLimit = usageLimit;
    checkRules();
  }

  /**
   * Hết hạn ngay (không xoá, để giữ lịch sử). Voucher chưa bắt đầu thì lùi {@code validFrom} về ngay trước
   * {@code now}, vì DB bắt buộc {@code validFrom < validTo}.
   */
  public void expire(Instant now) {
    if (!validFrom.isBefore(now)) {
      validFrom = now.minusSeconds(1);
    }
    validTo = now;
  }

  /** Hết hạn hoặc chưa tới ngày bắt đầu. */
  public boolean isOutsideWindow(Instant now) {
    return now.isBefore(validFrom) || !now.isBefore(validTo);
  }

  public boolean isExhausted() {
    return timesUsed >= usageLimit;
  }

  // Cùng các luật với ràng buộc CHECK của bảng vouchers (V13), báo 400 trước khi DB ném lỗi thành 500.
  private void checkRules() {
    if (!validFrom.isBefore(validTo)) {
      throw BusinessException.validation("validFrom must be before validTo");
    }
    if (usageLimit < timesUsed) {
      throw BusinessException.validation("usageLimit cannot be below timesUsed (" + timesUsed + ")");
    }
  }
}
