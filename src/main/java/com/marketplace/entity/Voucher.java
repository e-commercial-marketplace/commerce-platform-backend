package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Voucher của nền tảng, khoá chính là {@code code} (đã chuẩn hoá viết hoa). Dùng được khi
 * {@code validFrom <= now < validTo} và {@code timesUsed < usageLimit}. Lượt dùng được trừ bằng một câu
 * UPDATE có điều kiện ở {@code VoucherRepository.consume}, không qua entity, để nhiều checkout cùng lúc
 * không vượt {@code usageLimit}.
 */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

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
}
