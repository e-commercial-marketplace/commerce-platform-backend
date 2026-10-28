-- Phase 10 RAG: các đoạn điều khoản dành cho khách hàng và vector embedding của chúng.
-- Dữ liệu được nạp lại từ rag/marketplace-terms.vi.md mỗi lần ứng dụng khởi động (PolicyKnowledge).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE policy_chunks (
  id        BIGSERIAL PRIMARY KEY,
  topic     TEXT NOT NULL,
  title     TEXT NOT NULL,
  content   TEXT NOT NULL,
  -- 1536 chiều: kích thước vector của text-embedding-3-small.
  embedding vector(1536) NOT NULL
);
