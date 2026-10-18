-- V13: complete the domain tables represented by the JPA entities.
-- V2-V11 were placeholder migrations and are intentionally not rewritten because Flyway may have
-- already recorded their checksums in existing environments. The actual schema is added here.

-- V1 already creates users; add the optional profile columns from the logical domain model.
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(500);

-- Normalize any existing legacy rows before enforcing the role/status invariant.
UPDATE users
SET seller_status = 'PENDING'
WHERE role = 'SELLER' AND seller_status IS NULL;
UPDATE users
SET seller_status = NULL
WHERE role <> 'SELLER' AND seller_status IS NOT NULL;

ALTER TABLE users
  ADD CONSTRAINT ck_users_seller_status_role
  CHECK (
    (role = 'SELLER' AND seller_status IS NOT NULL)
    OR (role <> 'SELLER' AND seller_status IS NULL)
  );

CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(120) NOT NULL,
  CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  image_url VARCHAR(2048),
  price NUMERIC(19, 2) NOT NULL CHECK (price > 0),
  stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
  rating_average NUMERIC(3, 2) NOT NULL DEFAULT 0 CHECK (rating_average BETWEEN 0 AND 5),
  review_count INTEGER NOT NULL DEFAULT 0 CHECK (review_count >= 0),
  active BOOLEAN NOT NULL DEFAULT FALSE,
  category_id UUID NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
  seller_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT ck_products_active_has_image CHECK (NOT active OR image_url IS NOT NULL)
);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_seller_id ON products (seller_id);
CREATE INDEX idx_products_active ON products (active);

CREATE TABLE cart_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  quantity INTEGER NOT NULL CHECK (quantity >= 1),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
  CONSTRAINT uq_cart_items_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);

CREATE TABLE vouchers (
  code VARCHAR(64) PRIMARY KEY,
  description TEXT,
  discount_percent INTEGER NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
  valid_from TIMESTAMPTZ NOT NULL,
  valid_to TIMESTAMPTZ NOT NULL,
  usage_limit INTEGER NOT NULL CHECK (usage_limit >= 0),
  times_used INTEGER NOT NULL DEFAULT 0 CHECK (times_used >= 0),
  CONSTRAINT ck_vouchers_time_window CHECK (valid_to > valid_from),
  CONSTRAINT ck_vouchers_usage_limit CHECK (times_used <= usage_limit)
);

CREATE TABLE orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'DELIVERED', 'CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  shipping_address VARCHAR(1000) NOT NULL,
  shipping_fee NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
  discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
  total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  voucher_code VARCHAR(64) REFERENCES vouchers (code) ON DELETE RESTRICT
);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

CREATE TABLE order_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_name VARCHAR(255) NOT NULL,
  unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
  quantity INTEGER NOT NULL CHECK (quantity >= 1),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
  cancel_reason TEXT,
  order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
  seller_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_seller_id ON order_items (seller_id);

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
  method VARCHAR(20) NOT NULL CHECK (method IN ('COD', 'VNPAY')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
  transaction_id VARCHAR(255),
  paid_at TIMESTAMPTZ,
  order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_payments_transaction_id ON payments (transaction_id)
  WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_payments_order_id ON payments (order_id);

CREATE TABLE shipments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  delivery_method VARCHAR(30) NOT NULL CHECK (delivery_method IN ('PLATFORM_DELIVERY', 'SELF_DELIVERY')),
  status VARCHAR(20) NOT NULL CHECK (
    status IN ('PENDING', 'PREPARING', 'SHIPPED', 'DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED')
  ),
  tracking_code VARCHAR(255),
  shipping_fee NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
  shipped_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  order_id UUID NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
  seller_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  CONSTRAINT uq_shipments_order_seller UNIQUE (order_id, seller_id),
  CONSTRAINT ck_shipments_shipped_data CHECK (
    status <> 'SHIPPED' OR (tracking_code IS NOT NULL AND shipped_at IS NOT NULL)
  ),
  CONSTRAINT ck_shipments_delivered_data CHECK (
    status <> 'DELIVERED' OR delivered_at IS NOT NULL
  )
);
CREATE INDEX idx_shipments_seller_id ON shipments (seller_id);
CREATE INDEX idx_shipments_status ON shipments (status);
CREATE INDEX idx_shipments_order_id ON shipments (order_id);

-- The cross-row invariant that shipment.order_id/seller_id match the mapped order item's
-- order_id/seller_id is enforced by ShipmentService in the same transaction when that feature lands.
CREATE TABLE shipment_order_items (
  shipment_id UUID NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
  order_item_id UUID NOT NULL REFERENCES order_items (id) ON DELETE RESTRICT,
  PRIMARY KEY (shipment_id, order_item_id),
  CONSTRAINT uq_shipment_order_items_order_item UNIQUE (order_item_id)
);

CREATE TABLE reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
  CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_reviews_product_id ON reviews (product_id);

CREATE TABLE conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_message_at TIMESTAMPTZ,
  customer_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  seller_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  product_id UUID REFERENCES products (id) ON DELETE SET NULL,
  order_id UUID REFERENCES orders (id) ON DELETE SET NULL,
  CONSTRAINT uq_conversations_customer_seller UNIQUE (customer_id, seller_id)
);

CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  content TEXT NOT NULL,
  image_url VARCHAR(2048),
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);

CREATE TABLE ai_chat_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_chat_sessions_user_created ON ai_chat_sessions (user_id, created_at);

CREATE TABLE ai_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
  content TEXT NOT NULL,
  confidence NUMERIC(5, 4) CHECK (confidence BETWEEN 0 AND 1),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  session_id UUID NOT NULL REFERENCES ai_chat_sessions (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_messages_session_created ON ai_messages (session_id, created_at);
