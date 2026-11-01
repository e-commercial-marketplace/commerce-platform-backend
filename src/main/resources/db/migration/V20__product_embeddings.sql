-- Tìm product theo nghĩa (fallback khi LIKE không ra gì): mỗi product một vector embedding.
-- ProductIndexer đồng bộ bảng này mỗi 10 phút; JPA không map bảng này (test H2 không có pgvector).
CREATE TABLE product_embeddings (
  product_id   UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  -- md5 của đoạn chữ đã embedding: chữ không đổi thì không gọi provider lại.
  content_hash TEXT NOT NULL,
  -- 3072 chiều như policy_chunks (gemini-embedding-001). Không index: vài chục dòng, và index của
  -- pgvector không hỗ trợ quá 2000 chiều.
  embedding    vector(3072) NOT NULL
);
