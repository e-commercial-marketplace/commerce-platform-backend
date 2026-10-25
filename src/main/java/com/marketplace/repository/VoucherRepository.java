package com.marketplace.repository;

import com.marketplace.entity.Voucher;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Voucher của nền tảng; khoá chính là {@code code} đã chuẩn hoá viết hoa. */
public interface VoucherRepository extends JpaRepository<Voucher, String> {

  /**
   * Trừ một lượt dùng nếu voucher còn hạn và còn lượt, trong MỘT câu UPDATE có điều kiện (FR-VCH-04): hai
   * checkout cùng lúc dùng lượt cuối thì chỉ một bên đổi được dòng.
   *
   * @return 1 nếu đã trừ lượt; 0 nếu không có voucher, hết hạn hoặc hết lượt (service đọc lại để biết vì sao)
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Voucher v set v.timesUsed = v.timesUsed + 1 where v.code = :code"
          + " and v.validFrom <= :now and v.validTo > :now and v.timesUsed < v.usageLimit")
  int consume(@Param("code") String code, @Param("now") Instant now);
}
