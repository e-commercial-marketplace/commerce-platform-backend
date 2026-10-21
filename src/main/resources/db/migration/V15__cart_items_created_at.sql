-- V15: cart_items.created_at (Phase 3 Cart). Giỏ hàng hiển thị sản phẩm mới thêm nhất lên đầu.
-- Các dòng đã có trước migration nhận thời điểm chạy migration (DEFAULT now()).
-- Cộng dồn số lượng vào dòng cũ KHÔNG đổi created_at, nên vị trí của sản phẩm trong giỏ giữ nguyên.
ALTER TABLE cart_items ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Đọc giỏ của một customer theo thứ tự mới nhất trước.
CREATE INDEX idx_cart_items_user_created ON cart_items (user_id, created_at);
