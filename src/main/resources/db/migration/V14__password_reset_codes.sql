-- V14: password_reset_codes (bổ sung Phase 1: quên mật khẩu và đặt lại bằng mã gửi qua email).
-- Chỉ lưu hash của mã 6 chữ số (BCrypt); mã thô chỉ nằm trong email gửi cho người dùng.
-- Mã hết hạn sau expires_at, khoá sau khi sai quá số lần cho phép (attempts), và chỉ dùng một lần (used_at).
CREATE TABLE password_reset_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  code_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tra mã mới nhất và đếm số mã đã cấp trong một giờ của từng user.
CREATE INDEX idx_password_reset_codes_user_created ON password_reset_codes (user_id, created_at);
