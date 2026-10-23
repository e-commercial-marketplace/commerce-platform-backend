package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Đơn vị vận chuyển do admin quản lý (GHN, GHTK...). Seller chọn một đơn vị đang hoạt động khi giao qua
 * nền tảng. Không bao giờ xoá: tắt đi thì seller không chọn được nữa, nhưng shipment cũ vẫn giữ đơn vị.
 * Cột {@code created_at} do DB tự điền nên không map ở đây.
 */
@Entity
@Table(name = "carriers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Carrier {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "name", nullable = false, length = 100, unique = true)
  private String name;

  @Column(name = "active", nullable = false)
  private boolean active;

  /** Đơn vị mới luôn đang hoạt động. */
  public static Carrier create(String name) {
    Carrier carrier = new Carrier();
    carrier.name = name;
    carrier.active = true;
    return carrier;
  }

  public void rename(String name) {
    this.name = name;
  }

  /** Bật hoặc tắt; tắt thì seller không còn thấy đơn vị này. */
  public void setActive(boolean active) {
    this.active = active;
  }
}
