-- Phase 10 RAG: các đoạn điều khoản dành cho khách hàng và vector embedding của chúng.
-- Dữ liệu được nạp lại từ rag/marketplace-terms.vi.md mỗi lần ứng dụng khởi động (PolicyKnowledge).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE policy_chunks (
  id        BIGSERIAL PRIMARY KEY,
  topic     TEXT NOT NULL,
  title     TEXT NOT NULL,
  content   TEXT NOT NULL,
  -- 3072 chiều: kích thước vector của gemini-embedding-001. Không tạo index vì chỉ có vài chục dòng
  -- (index của pgvector cũng không hỗ trợ quá 2000 chiều).
  embedding vector(3072) NOT NULL
);
