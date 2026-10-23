-- Phase 7: khách xác nhận đã nhận hàng (hoặc hệ thống tự xác nhận sau 3 ngày) chuyển order từ DELIVERED
-- sang COMPLETED. Ràng buộc CHECK của cột status (tạo ở V13, Postgres tự đặt tên orders_status_check)
-- phải nhận thêm giá trị mới.
ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
  CHECK (status IN ('PENDING', 'PAID', 'DELIVERED', 'COMPLETED', 'CANCELLED'));
