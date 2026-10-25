-- =====================================================================
-- Seed dữ liệu mẫu cho môi trường dev/demo.
-- KHÔNG phải Flyway migration: Flyway chỉ chạy các file trong
-- src/main/resources/db/migration. File này chạy thủ công, xem
-- backend/seed/README.md để biết cách nạp.
--
-- Nội dung:
--   - 1 CUSTOMER, 1 ADMIN (dev only) và 11 SELLER: 9 shop đã duyệt (có sản phẩm),
--     1 seller PENDING và 1 seller SUSPENDED (không có sản phẩm, để thử luồng admin)
--   - 9 danh mục
--   - 36 sản phẩm thật, ảnh thật (Wikimedia Commons), giá tham khảo thị trường VN,
--     chia cho 9 shop; tên shop là tên tự đặt, không phải cửa hàng có thật
--   - 5 đơn vị vận chuyển (admin quản lý): 4 đang hoạt động, 1 đã tắt (cần bảng carriers, V16)
--   - 6 voucher của nền tảng: 2 dùng được, 1 còn một lượt, 1 hết lượt, 1 hết hạn, 1 chưa bắt đầu
--
-- Đặc điểm:
--   - Idempotent: UUID cố định + ON CONFLICT (id) DO UPDATE, chạy lại không nhân đôi.
--   - Không tạo review/đơn hàng giả: rating_average = 0, review_count = 0.
--   - Mật khẩu băm bằng pgcrypto (BCrypt $2a$, tương thích BCryptPasswordEncoder).
-- =====================================================================

BEGIN;

-- pgcrypto: crypt()/gen_salt() dùng để băm mật khẩu bằng BCrypt.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- 1) Tài khoản mẫu: 1 customer, 1 admin và nhiều seller
--    Mật khẩu dev: Customer@123 / Seller@123 / Admin@123
--    ADMIN ở đây chỉ dành cho dev. Production phải tạo admin qua kênh
--    vận hành, không qua đăng ký công khai.
-- ---------------------------------------------------------------------
INSERT INTO users (
  id, email, password_hash, full_name, role, seller_status, enabled, phone, address, created_at, updated_at
)
VALUES
  (
    '11111111-1111-4111-8111-000000000001',
    'customer@marketplace.dev',
    crypt('Customer@123', gen_salt('bf', 10)),
    'Nguyễn Thu Hà', 'CUSTOMER', NULL, TRUE,
    '0901234567', '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000002',
    'seller@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Trần Minh Khoa', 'SELLER', 'APPROVED', TRUE,
    '0912345678', '45 Lê Lợi, Quận 3, TP. Hồ Chí Minh', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000003',
    'admin@marketplace.dev',
    crypt('Admin@123', gen_salt('bf', 10)),
    'Quản trị viên', 'ADMIN', NULL, TRUE,
    '0987654321', '1 Trần Hưng Đạo, Quận 1, TP. Hồ Chí Minh', now(), now()
  ),
  -- Các shop bổ sung: mật khẩu chung Seller@123; tên shop chính là full_name.
  (
    '11111111-1111-4111-8111-000000000004',
    'shop.android@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Android Hub Đà Nẵng', 'SELLER', 'APPROVED', TRUE,
    '0935123456', '88 Nguyễn Văn Linh, Hải Châu, Đà Nẵng', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000005',
    'shop.laptop@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Laptop Minh Châu', 'SELLER', 'APPROVED', TRUE,
    '0978123456', '210 Thái Hà, Đống Đa, Hà Nội', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000006',
    'shop.audio@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Âm Thanh Việt', 'SELLER', 'APPROVED', TRUE,
    '0389123456', '56 Trần Quang Khải, Quận 1, TP. Hồ Chí Minh', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000007',
    'shop.wearable@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Đồng Hồ Thông Minh Huế', 'SELLER', 'APPROVED', TRUE,
    '0702123456', '15 Lê Lợi, Phú Hội, Huế', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000008',
    'shop.smarthome@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Nhà Thông Minh 247', 'SELLER', 'APPROVED', TRUE,
    '0567123456', '102 Cộng Hòa, Tân Bình, TP. Hồ Chí Minh', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000009',
    'shop.camera@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Góc Máy Ảnh Sài Gòn', 'SELLER', 'APPROVED', TRUE,
    '0868123456', '34 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000010',
    'shop.accessories@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Phụ Kiện Số Cần Thơ', 'SELLER', 'APPROVED', TRUE,
    '0329123456', '7 Đường 30/4, Ninh Kiều, Cần Thơ', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000011',
    'shop.sneaker@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Sneaker Phố Cổ', 'SELLER', 'APPROVED', TRUE,
    '0943123456', '21 Hàng Bông, Hoàn Kiếm, Hà Nội', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000012',
    'shop.pending@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Shop Mới Chờ Duyệt', 'SELLER', 'PENDING', TRUE,
    '0384123456', '9 Phan Chu Trinh, Hội An, Quảng Nam', now(), now()
  ),
  (
    '11111111-1111-4111-8111-000000000013',
    'shop.suspended@marketplace.dev',
    crypt('Seller@123', gen_salt('bf', 10)),
    'Shop Đang Tạm Khóa', 'SELLER', 'SUSPENDED', TRUE,
    '0795123456', '60 Trần Phú, Nha Trang, Khánh Hòa', now(), now()
  )
ON CONFLICT (id) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  full_name = EXCLUDED.full_name,
  role = EXCLUDED.role,
  seller_status = EXCLUDED.seller_status,
  enabled = EXCLUDED.enabled,
  phone = EXCLUDED.phone,
  address = EXCLUDED.address,
  updated_at = now();

-- ---------------------------------------------------------------------
-- 2) Danh mục
-- ---------------------------------------------------------------------
INSERT INTO categories (id, name)
VALUES
  ('22222222-2222-4222-8222-000000000001', 'Điện thoại'),
  ('22222222-2222-4222-8222-000000000002', 'Laptop'),
  ('22222222-2222-4222-8222-000000000003', 'Máy tính bảng'),
  ('22222222-2222-4222-8222-000000000004', 'Tai nghe & Loa'),
  ('22222222-2222-4222-8222-000000000005', 'Đồng hồ thông minh'),
  ('22222222-2222-4222-8222-000000000006', 'Gia dụng thông minh'),
  ('22222222-2222-4222-8222-000000000007', 'Máy ảnh & Flycam'),
  ('22222222-2222-4222-8222-000000000008', 'Phụ kiện công nghệ'),
  ('22222222-2222-4222-8222-000000000009', 'Thể thao & Thời trang')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- ---------------------------------------------------------------------
-- 3) Sản phẩm — chia cho 9 shop (sản phẩm Apple thuộc seller@marketplace.dev)
--    Giá là giá tham khảo thị trường Việt Nam (10/2026), đơn vị VND.
--    Ảnh: Wikimedia Commons (giấy phép tự do, hotlink được); mỗi dòng ghi
--    kèm tên file gốc để tra cứu. Đã kiểm tra toàn bộ URL trả HTTP 200.
-- ---------------------------------------------------------------------
INSERT INTO products (
  id, name, description, image_url, price, stock, active, category_id, seller_id
)
VALUES
  -- Điện thoại
  (
    '33333333-3333-4333-8333-000000000001',
    'iPhone 16 Pro Max 256GB',
    'Chip A18 Pro, màn hình Super Retina XDR 6.9 inch ProMotion 120Hz, camera chính 48MP, '
    'ultra wide 48MP, tele 12MP zoom quang 5x, khung titan, cổng USB-C.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e2/IPhone_16_Pro_Max_Desert_Titanium_Rear.png/1280px-IPhone_16_Pro_Max_Desert_Titanium_Rear.png',
    30490000, 25, TRUE,
    '22222222-2222-4222-8222-000000000001', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000002',
    'iPhone 15 Pro 128GB',
    'Chip A17 Pro, màn hình 6.1 inch ProMotion 120Hz, camera 48MP, khung titan, cổng USB-C, '
    'nút Action thay cho công tắc chuông.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Apple_iPhone_15_Pro.jpg/1280px-Apple_iPhone_15_Pro.jpg',
    22990000, 18, TRUE,
    '22222222-2222-4222-8222-000000000001', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000003',
    'Samsung Galaxy S25 Ultra 12GB/256GB',
    'Snapdragon 8 Elite for Galaxy, màn hình 6.9 inch QHD+ 120Hz, camera 200MP, bút S Pen, '
    'pin 5000mAh, khung titan, One UI 7.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b0/Samsung_Galaxy_S25_Ultra.jpg/1280px-Samsung_Galaxy_S25_Ultra.jpg',
    27990000, 30, TRUE,
    '22222222-2222-4222-8222-000000000001', '11111111-1111-4111-8111-000000000004'
  ),
  (
    '33333333-3333-4333-8333-000000000004',
    'Google Pixel 8 Pro 128GB',
    'Chip Tensor G3, màn hình LTPO 6.7 inch 120Hz, camera 50MP, Android gốc với 7 năm cập nhật, '
    'tính năng AI chỉnh ảnh.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/21/Google_Pixel_8_Pro.jpg/1280px-Google_Pixel_8_Pro.jpg',
    18990000, 12, TRUE,
    '22222222-2222-4222-8222-000000000001', '11111111-1111-4111-8111-000000000004'
  ),

  -- Laptop
  (
    '33333333-3333-4333-8333-000000000005',
    'MacBook Air M2 13 inch 8GB/256GB',
    'Chip Apple M2, màn hình Liquid Retina 13.6 inch, 8GB RAM, SSD 256GB, thời lượng pin tới 18 giờ, '
    'quạt tản nhiệt không tiếng ồn.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9f/M2_Macbook_Air_Starlight_model.jpg/1280px-M2_Macbook_Air_Starlight_model.jpg',
    19990000, 20, TRUE,
    '22222222-2222-4222-8222-000000000002', '11111111-1111-4111-8111-000000000005'
  ),
  (
    '33333333-3333-4333-8333-000000000006',
    'MacBook Pro 14 inch M3 8GB/512GB',
    'Chip Apple M3, màn hình Liquid Retina XDR 14.2 inch 120Hz, 8GB RAM, SSD 512GB, '
    'cổng HDMI và khe thẻ SDXC.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a6/M3_Macbook_Pro_14_inch_Space_Grey_model.jpg/1280px-M3_Macbook_Pro_14_inch_Space_Grey_model.jpg',
    39990000, 9, TRUE,
    '22222222-2222-4222-8222-000000000002', '11111111-1111-4111-8111-000000000005'
  ),
  (
    '33333333-3333-4333-8333-000000000007',
    'Lenovo ThinkPad X1 Carbon Gen 12',
    'Intel Core Ultra 7, màn hình 14 inch 2.8K OLED, 16GB RAM, SSD 512GB, trọng lượng từ 1.09kg, '
    'chuẩn doanh nghiệp với bảo mật vân tay và IR camera.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5a/Lenovo_ThinkPad_X1_Carbon_014_%2810774123843%29.jpg/1280px-Lenovo_ThinkPad_X1_Carbon_014_%2810774123843%29.jpg',
    44990000, 7, TRUE,
    '22222222-2222-4222-8222-000000000002', '11111111-1111-4111-8111-000000000005'
  ),
  (
    '33333333-3333-4333-8333-000000000008',
    'ASUS ROG Zephyrus G14 (2024)',
    'AMD Ryzen 9 8945HS, GPU RTX 4060, màn hình OLED 14 inch 3K 120Hz, 16GB RAM, SSD 1TB, '
    'thân nhôm mỏng nhẹ cho gaming và sáng tạo.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c6/ASUS_ROG_Zephyrus_2026-08-15_G14.jpg/1280px-ASUS_ROG_Zephyrus_2026-08-15_G14.jpg',
    36990000, 11, TRUE,
    '22222222-2222-4222-8222-000000000002', '11111111-1111-4111-8111-000000000005'
  ),

  -- Máy tính bảng
  (
    '33333333-3333-4333-8333-000000000009',
    'iPad Air 11 inch M2 128GB',
    'Chip Apple M2, màn hình Liquid Retina 11 inch, 128GB, hỗ trợ Apple Pencil Pro và Magic Keyboard, '
    'camera trước 12MP đặt ngang.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8d/IPad_Air.png/1280px-IPad_Air.png',
    16990000, 22, TRUE,
    '22222222-2222-4222-8222-000000000003', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000010',
    'iPad Pro 11 inch M4 256GB',
    'Chip Apple M4, màn hình Ultra Retina XDR Tandem OLED 11 inch, 256GB, mỏng 5.3mm, '
    'hỗ trợ Apple Pencil Pro.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c8/M4_iPad_Pro_series_-_1.jpg/1280px-M4_iPad_Pro_series_-_1.jpg',
    28990000, 14, TRUE,
    '22222222-2222-4222-8222-000000000003', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000011',
    'Samsung Galaxy Tab S9 128GB',
    'Snapdragon 8 Gen 2 for Galaxy, màn hình 11 inch AMOLED 120Hz, kèm bút S Pen, pin 8400mAh, '
    'chuẩn IP68.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/05/Samsung_Galaxy_Tab_S9.png/1280px-Samsung_Galaxy_Tab_S9.png',
    18990000, 16, TRUE,
    '22222222-2222-4222-8222-000000000003', '11111111-1111-4111-8111-000000000004'
  ),

  -- Tai nghe & Loa
  (
    '33333333-3333-4333-8333-000000000012',
    'Apple AirPods Pro 2 (USB-C)',
    'Chip H2, chống ồn chủ động ANC, âm thanh không gian, hộp sạc USB-C, chống nước IP54, '
    'thời lượng pin tới 6 giờ mỗi lần sạc.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2f/AirPods_Pro_%282nd_generation%29.jpg/1280px-AirPods_Pro_%282nd_generation%29.jpg',
    5490000, 40, TRUE,
    '22222222-2222-4222-8222-000000000004', '11111111-1111-4111-8111-000000000006'
  ),
  (
    '33333333-3333-4333-8333-000000000013',
    'Apple AirPods Max (USB-C)',
    'Tai nghe over-ear, chip H2, chống ồn chủ động ANC, âm thanh không gian cá nhân hóa, '
    'cổng USB-C, khung nhôm và đệm lưới.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7e/Apple_airpods_max_1.jpg/1280px-Apple_airpods_max_1.jpg',
    13490000, 8, TRUE,
    '22222222-2222-4222-8222-000000000004', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000014',
    'Loa Bluetooth Bose SoundLink Micro',
    'Loa di động nhỏ gọn, chống nước IPX7, dây đeo silicon, pin khoảng 6 giờ, '
    'âm thanh cân bằng cho nhu cầu nghe hàng ngày.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ec/Bose_SoundLink_Micro.jpg/1280px-Bose_SoundLink_Micro.jpg',
    2990000, 26, TRUE,
    '22222222-2222-4222-8222-000000000004', '11111111-1111-4111-8111-000000000006'
  ),
  (
    '33333333-3333-4333-8333-000000000015',
    'Loa Bluetooth JBL Charge 4',
    'Loa công suất 30W, pin 7500mAh dùng làm sạc dự phòng, chống nước IPX7, '
    'thời lượng pin tới 20 giờ, kết nối Bluetooth 4.2.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/33/JBL_Charge_4_1v2.jpg/1280px-JBL_Charge_4_1v2.jpg',
    2990000, 19, TRUE,
    '22222222-2222-4222-8222-000000000004', '11111111-1111-4111-8111-000000000006'
  ),

  -- Đồng hồ thông minh
  (
    '33333333-3333-4333-8333-000000000016',
    'Apple Watch Series 9 GPS 41mm',
    'Chip S9, màn hình 1.9 inch Always-On, thao tác hai chạm, theo dõi sức khỏe và giấc ngủ, '
    'GPS, chống nước WR50.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/39/Apple_Watch_Series_9_1_2023-11-14.jpg/1280px-Apple_Watch_Series_9_1_2023-11-14.jpg',
    8990000, 21, TRUE,
    '22222222-2222-4222-8222-000000000005', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000017',
    'Apple Watch Ultra 2 49mm',
    'Vỏ titan 49mm, màn hình sáng 3000 nits, pin tới 36 giờ, GPS hai băng tần, '
    'chống nước 100m, phím Action.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/33/Apple_Watch_Ultra_2.jpg/1280px-Apple_Watch_Ultra_2.jpg',
    20990000, 10, TRUE,
    '22222222-2222-4222-8222-000000000005', '11111111-1111-4111-8111-000000000002'
  ),
  (
    '33333333-3333-4333-8333-000000000018',
    'Garmin Forerunner 955 Solar',
    'Đồng hồ chạy bộ GPS, sạc năng lượng mặt trời, pin tới 20 ngày, bản đồ, VO2 max, '
    'đa môn thể thao, theo dõi giấc ngủ.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/80/Garmin_955_Solar.jpg/1280px-Garmin_955_Solar.jpg',
    12990000, 13, TRUE,
    '22222222-2222-4222-8222-000000000005', '11111111-1111-4111-8111-000000000007'
  ),
  (
    '33333333-3333-4333-8333-000000000019',
    'Casio G-Shock GW-M5610U',
    'Đồng hồ G-Shock Tough Solar, chống sốc, chống nước 200m, sóng radio đa băng tần, '
    'đèn LED, lịch vạn niên.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/64/CASIO_G-Shock_GW-M5610U.jpg/1280px-CASIO_G-Shock_GW-M5610U.jpg',
    3290000, 35, TRUE,
    '22222222-2222-4222-8222-000000000005', '11111111-1111-4111-8111-000000000007'
  ),

  -- Gia dụng thông minh
  (
    '33333333-3333-4333-8333-000000000020',
    'Xiaomi Mi Robot Vacuum-Mop',
    'Robot hút bụi lau nhà, lực hút 2100Pa, cảm biến Lidar, điều khiển qua app, pin 5200mAh, '
    'tự động quay về đế sạc.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f7/XiaomiMiRobotVacuumCleanerRobot.jpg/1280px-XiaomiMiRobotVacuumCleanerRobot.jpg',
    5990000, 17, TRUE,
    '22222222-2222-4222-8222-000000000006', '11111111-1111-4111-8111-000000000008'
  ),
  (
    '33333333-3333-4333-8333-000000000021',
    'iRobot Roomba i7+',
    'Robot hút bụi tự đổ rác, điều hướng thông minh, điều khiển qua app, '
    'phù hợp nhà có thú nuôi, đế tự đổ rác Clean Base.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b6/IRobot_Roomba_i7%2B.jpg/1280px-IRobot_Roomba_i7%2B.jpg',
    16990000, 6, TRUE,
    '22222222-2222-4222-8222-000000000006', '11111111-1111-4111-8111-000000000008'
  ),
  (
    '33333333-3333-4333-8333-000000000022',
    'Nespresso Vertuo Pop',
    'Máy pha cà phê cảm ứng, công nghệ ly tâm, pha 5 cỡ cốc, khởi động trong 30 giây, '
    'thiết kế nhỏ gọn.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/81/Nespresso_Vertuo_Pop.jpg/1280px-Nespresso_Vertuo_Pop.jpg',
    3290000, 24, TRUE,
    '22222222-2222-4222-8222-000000000006', '11111111-1111-4111-8111-000000000008'
  ),
  (
    '33333333-3333-4333-8333-000000000023',
    'KitchenAid Artisan 5KSM150 4.8L',
    'Máy trộn bột 4.8L, 10 tốc độ, động cơ 300W, thân kim loại, kèm 3 phụ kiện cơ bản, '
    'phù hợp làm bánh và nhào bột.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/42/Red_KitchenAid_Artisan.jpg/1280px-Red_KitchenAid_Artisan.jpg',
    15990000, 5, TRUE,
    '22222222-2222-4222-8222-000000000006', '11111111-1111-4111-8111-000000000008'
  ),
  (
    '33333333-3333-4333-8333-000000000024',
    'Dyson Supersonic',
    'Máy sấy tóc 1600W, công nghệ điều khiển nhiệt thông minh, 4 chế độ nhiệt, '
    'phụ kiện gắn từ tính, bảo vệ tóc khỏi hư tổn do nhiệt.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a0/Dyson_Supersonic_Hair_Dryer_3_2017-01-28.jpg/1280px-Dyson_Supersonic_Hair_Dryer_3_2017-01-28.jpg',
    11490000, 15, TRUE,
    '22222222-2222-4222-8222-000000000006', '11111111-1111-4111-8111-000000000008'
  ),

  -- Máy ảnh & Flycam
  (
    '33333333-3333-4333-8333-000000000025',
    'Sony Alpha 7R IV (body)',
    'Máy ảnh mirrorless full-frame 61MP, màn hình lật 3 inch, chụp liên tiếp 10fps, '
    'quay video 4K, hệ ngàm Sony E.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/08/Sony_Alpha_7R_IV_body_with_Sony_Zeiss_55mm_lens.jpg/1280px-Sony_Alpha_7R_IV_body_with_Sony_Zeiss_55mm_lens.jpg',
    54990000, 4, TRUE,
    '22222222-2222-4222-8222-000000000007', '11111111-1111-4111-8111-000000000009'
  ),
  (
    '33333333-3333-4333-8333-000000000026',
    'Canon EOS R6 (body)',
    'Mirrorless full-frame 20.1MP, chống rung IBIS 8 stop, quay 4K 60fps, '
    'lấy nét Dual Pixel CMOS AF II, 2 khe thẻ SD.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8d/Canon_R6_und_RF_85_1%2C2-8068.jpg/1280px-Canon_R6_und_RF_85_1%2C2-8068.jpg',
    39990000, 6, TRUE,
    '22222222-2222-4222-8222-000000000007', '11111111-1111-4111-8111-000000000009'
  ),
  (
    '33333333-3333-4333-8333-000000000027',
    'Fujifilm X-T4 (body)',
    'Mirrorless APS-C 26.1MP, chống rung IBIS 6.5 stop, quay 4K 60fps, màn hình lật, '
    'chụp liên tiếp 15fps, giả lập phim Fujifilm.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5e/Fujifilm_X-T4_29_apr_2020a.jpg/1280px-Fujifilm_X-T4_29_apr_2020a.jpg',
    28990000, 8, TRUE,
    '22222222-2222-4222-8222-000000000007', '11111111-1111-4111-8111-000000000009'
  ),
  (
    '33333333-3333-4333-8333-000000000028',
    'DJI Mini 4 Pro',
    'Flycam dưới 249g, camera 4K/60fps HDR cảm biến 1/1.3 inch, tránh vật cản 360 độ, '
    'thời gian bay tới 34 phút, truyền hình ảnh 20km.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/94/2024_Dron_DJI_Mini_4_Pro_%2801%29.jpg/1280px-2024_Dron_DJI_Mini_4_Pro_%2801%29.jpg',
    22990000, 9, TRUE,
    '22222222-2222-4222-8222-000000000007', '11111111-1111-4111-8111-000000000009'
  ),
  (
    '33333333-3333-4333-8333-000000000029',
    'GoPro HERO9 Black',
    'Action camera quay 5K/30fps, cảm biến 23.6MP, màn hình trước màu, chống nước 10m, '
    'chống rung HyperSmooth 3.0.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7e/GoPro_Hero_9_Black_-_Front.jpg/1280px-GoPro_Hero_9_Black_-_Front.jpg',
    6990000, 12, TRUE,
    '22222222-2222-4222-8222-000000000007', '11111111-1111-4111-8111-000000000009'
  ),

  -- Phụ kiện công nghệ
  (
    '33333333-3333-4333-8333-000000000030',
    'Logitech MX Master 3S',
    'Chuột không dây, cảm biến 8000 DPI, cuộn MagSpeed, kết nối Logi Bolt và USB-C, '
    'pin tới 70 ngày, gần như không tiếng ồn.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bc/Logitech_MX_Master_3S_HS11.jpg/1280px-Logitech_MX_Master_3S_HS11.jpg',
    2490000, 38, TRUE,
    '22222222-2222-4222-8222-000000000008', '11111111-1111-4111-8111-000000000010'
  ),
  (
    '33333333-3333-4333-8333-000000000031',
    'SanDisk Extreme Portable SSD 1TB',
    'SSD di động 1TB, tốc độ đọc tới 1050MB/s, chuẩn chống bụi nước IP65, cổng USB-C, '
    'bảo hành 5 năm.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7c/SanDisk_Extreme_Portable_SSD_1_TB_-_front_view.jpg/1280px-SanDisk_Extreme_Portable_SSD_1_TB_-_front_view.jpg',
    2690000, 27, TRUE,
    '22222222-2222-4222-8222-000000000008', '11111111-1111-4111-8111-000000000010'
  ),
  (
    '33333333-3333-4333-8333-000000000032',
    'Apple TV 4K 64GB (3rd gen)',
    'Hộp giải trí 4K HDR, chip A15 Bionic, điều khiển Siri Remote, hỗ trợ Dolby Vision và '
    'Dolby Atmos, cổng HDMI 2.1.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/54/Apple_TV_4K_%283rd_Generation%2C_2022%29_with_Siri_Remote.jpg/1280px-Apple_TV_4K_%283rd_Generation%2C_2022%29_with_Siri_Remote.jpg',
    3990000, 23, TRUE,
    '22222222-2222-4222-8222-000000000008', '11111111-1111-4111-8111-000000000002'
  ),

  -- Thể thao & Thời trang
  (
    '33333333-3333-4333-8333-000000000033',
    'Nike Air Max 90',
    'Giày sneaker kinh điển với đệm Air Max, upper lưới kết hợp da, đế cao su bám tốt, '
    'phù hợp đi hàng ngày.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b3/Nike_Air_Max_90.jpg/1280px-Nike_Air_Max_90.jpg',
    3290000, 32, TRUE,
    '22222222-2222-4222-8222-000000000009', '11111111-1111-4111-8111-000000000011'
  ),
  (
    '33333333-3333-4333-8333-000000000034',
    'Adidas Superstar',
    'Giày sneaker da với mũi giày cao su hình vỏ sò đặc trưng, 3 sọc Adidas, đế cao su, '
    'thiết kế unisex.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Adidas_Superstar_shoes_pair.jpg/1280px-Adidas_Superstar_shoes_pair.jpg',
    2190000, 41, TRUE,
    '22222222-2222-4222-8222-000000000009', '11111111-1111-4111-8111-000000000011'
  ),
  (
    '33333333-3333-4333-8333-000000000035',
    'Dr. Martens 1460',
    'Bốt da 8 lỗ, đế AirWair, đường may vàng đặc trưng, thiết kế unisex, '
    'phù hợp nhiều phong cách.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/12/Dr._Martens_boots._Made_in_England.jpeg/1280px-Dr._Martens_boots._Made_in_England.jpeg',
    4590000, 14, TRUE,
    '22222222-2222-4222-8222-000000000009', '11111111-1111-4111-8111-000000000011'
  ),
  (
    '33333333-3333-4333-8333-000000000036',
    'Fjallraven Kanken Classic 16L',
    'Ba lô 16L vải Vinylon F chống nước, ngăn laptop 13 inch, thiết kế tối giản Bắc Âu, '
    'phù hợp đi học và du lịch ngắn ngày.',
    'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Fjallraven_Kanken_backpack_%28Unsplash%29.jpg/1280px-Fjallraven_Kanken_backpack_%28Unsplash%29.jpg',
    2890000, 29, TRUE,
    '22222222-2222-4222-8222-000000000009', '11111111-1111-4111-8111-000000000011'
  )
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  image_url = EXCLUDED.image_url,
  price = EXCLUDED.price,
  stock = EXCLUDED.stock,
  active = EXCLUDED.active,
  category_id = EXCLUDED.category_id,
  seller_id = EXCLUDED.seller_id;

-- ---------------------------------------------------------------------
-- 4) Đơn vị vận chuyển do admin quản lý (Phase 7, bảng carriers từ V16).
--    Seller chọn một đơn vị đang hoạt động khi giao qua nền tảng (PLATFORM_DELIVERY).
--    "Ninja Van" để tắt sẵn để thử: admin vẫn thấy, seller không thấy.
--    Tên là tên hãng vận chuyển có thật chỉ để demo; dự án không tích hợp API của các hãng.
-- ---------------------------------------------------------------------
INSERT INTO carriers (id, name, active)
VALUES
  ('55555555-5555-4555-8555-000000000001', 'Giao Hàng Nhanh (GHN)', TRUE),
  ('55555555-5555-4555-8555-000000000002', 'Giao Hàng Tiết Kiệm (GHTK)', TRUE),
  ('55555555-5555-4555-8555-000000000003', 'Viettel Post', TRUE),
  ('55555555-5555-4555-8555-000000000004', 'J&T Express', TRUE),
  ('55555555-5555-4555-8555-000000000005', 'Ninja Van', FALSE)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  active = EXCLUDED.active;

-- ---------------------------------------------------------------------
-- 5) Voucher của nền tảng (Phase 8). Khách nhập mã lúc checkout.
--    Khoảng hiệu lực tính theo now() lúc nạp, nên chạy lại seed sẽ "làm mới" hạn.
--    Mỗi mã để thử một trường hợp:
--      WELCOME10, SALE20  dùng được
--      FLASH50            còn đúng 1 lượt: checkout một lần là hết
--      SOLDOUT15          đã hết lượt (409 VOUCHER_EXHAUSTED); times_used đặt sẵn,
--                         không có đơn hàng thật đứng sau
--      EXPIRED30          đã hết hạn (409 VOUCHER_EXPIRED)
--      UPCOMING25         chưa tới ngày bắt đầu (409 VOUCHER_EXPIRED)
-- ---------------------------------------------------------------------
INSERT INTO vouchers (code, description, discount_percent, valid_from, valid_to, usage_limit, times_used)
VALUES
  ('WELCOME10',  'Giảm 10% cho đơn đầu tiên',           10, now() - interval '1 day',   now() + interval '90 days', 1000, 0),
  ('SALE20',     'Giảm 20% tháng này',                  20, now() - interval '1 day',   now() + interval '30 days',  100, 0),
  ('FLASH50',    'Flash sale 50%, chỉ một lượt',        50, now() - interval '1 day',   now() + interval '7 days',     1, 0),
  ('SOLDOUT15',  'Giảm 15%, đã hết lượt',               15, now() - interval '1 day',   now() + interval '30 days',    5, 5),
  ('EXPIRED30',  'Giảm 30%, đã hết hạn',                30, now() - interval '30 days', now() - interval '1 day',    100, 0),
  ('UPCOMING25', 'Giảm 25%, bắt đầu tuần sau',          25, now() + interval '7 days',  now() + interval '37 days',  100, 0)
-- Chạy lại: làm mới mô tả và hạn, giữ nguyên times_used và discount_percent (API cấm đổi % sau khi đã
-- có người dùng, vì đơn cũ tính lại giảm giá theo % hiện tại); usage_limit không thấp hơn số lượt đã dùng.
ON CONFLICT (code) DO UPDATE SET
  description = EXCLUDED.description,
  valid_from = EXCLUDED.valid_from,
  valid_to = EXCLUDED.valid_to,
  usage_limit = GREATEST(EXCLUDED.usage_limit, vouchers.times_used);

COMMIT;

-- ---------------------------------------------------------------------
-- Kiểm tra nhanh sau khi nạp (chạy tay nếu cần):
--   SELECT role, count(*) FROM users GROUP BY role ORDER BY role;
--   SELECT c.name, count(*) FROM products p JOIN categories c ON c.id = p.category_id
--   GROUP BY c.name ORDER BY c.name;
--   SELECT name, active FROM carriers ORDER BY name;
--   SELECT code, discount_percent, valid_from, valid_to, times_used, usage_limit FROM vouchers ORDER BY code;
-- ---------------------------------------------------------------------
