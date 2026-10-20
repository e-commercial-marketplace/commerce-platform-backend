package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Platform-issued discount code; usage concurrency is handled by transactional service logic. */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
