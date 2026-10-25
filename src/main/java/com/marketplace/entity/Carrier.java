package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Đơn vị vận chuyển do admin quản lý (GHN, GHTK...). Seller chọn một đơn vị đang hoạt động khi giao qua
 * nền tảng. Không bao giờ xoá: tắt đi thì seller không chọn được nữa, nhưng shipment cũ vẫn giữ đơn vị.
 * Cột {@code created_at} do DB tự điền nên không map ở đây.
 */
@Entity
@Table(name = "carriers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Carrier {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "name", nullable = false, length = 100, unique = true)
  private String name;

  @Column(name = "active", nullable = false)
  private boolean active;
}
