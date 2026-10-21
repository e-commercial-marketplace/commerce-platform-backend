package com.marketplace.repository;

import com.marketplace.entity.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lưu các lần thanh toán của order (Phase 4 chỉ tạo payment COD chờ thu tiền). */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {}
