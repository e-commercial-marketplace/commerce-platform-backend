-- Phase 7: đơn vị vận chuyển do admin quản lý. Shipment giao qua nền tảng (PLATFORM_DELIVERY) phải chọn
-- một đơn vị; shipment tự giao (SELF_DELIVERY) thì không. Đơn vị không bị xoá, chỉ tắt (active = false).
CREATE TABLE carriers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Tên duy nhất, không phân biệt hoa thường ("GHN" và "ghn" là một).
CREATE UNIQUE INDEX uq_carriers_name_lower ON carriers (lower(name));

ALTER TABLE shipments ADD COLUMN carrier_id UUID REFERENCES carriers (id) ON DELETE RESTRICT;
-- Có đơn vị khi và chỉ khi giao qua nền tảng.
ALTER TABLE shipments ADD CONSTRAINT ck_shipments_carrier
  CHECK ((delivery_method = 'PLATFORM_DELIVERY') = (carrier_id IS NOT NULL));
CREATE INDEX idx_shipments_carrier_id ON shipments (carrier_id);
