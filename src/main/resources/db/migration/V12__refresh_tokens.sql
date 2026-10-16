-- V12: refresh_tokens (Phase 1 Auth: rotation + revocation).
-- Only the SHA-256 hash of the refresh token is stored; the raw token is never persisted.
-- Rotation: successful refresh revokes the used row (revoked_at) and inserts a new row.
-- Logout/admin action: set revoked_at. Expired or revoked rows are rejected with 401.
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
