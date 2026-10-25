# Development seed data

Realistic demo data for a development/demo database: **1 customer, 1 admin and 11 sellers**, **9
categories** and **36 products** with real product information and real image URLs, spread over
9 shops so one cart can easily mix several sellers, plus **5 carriers** and **6 vouchers**.

It is **not** a Flyway migration. Flyway only runs files in `src/main/resources/db/migration`, so
this seed is never applied automatically to any environment.

## Files

| File | Purpose |
|---|---|
| `seed-dev.sql` | The seed data (idempotent: fixed UUIDs or voucher codes + `ON CONFLICT ... DO UPDATE`) |
| `apply-seed.ps1` | Applies `seed-dev.sql` with `psql` inside a throwaway Docker container, reading `backend/.env` |

## Accounts

| Role | Email | Password | Notes |
|---|---|---|---|
| `CUSTOMER` | `customer@marketplace.dev` | `Customer@123` | Regular shopper |
| `SELLER` | `seller@marketplace.dev` | `Seller@123` | Shop "Trần Minh Khoa", `seller_status = APPROVED`; Apple products |
| `SELLER` | `shop.android@marketplace.dev` | `Seller@123` | Shop "Android Hub Đà Nẵng", `seller_status = APPROVED` |
| `SELLER` | `shop.laptop@marketplace.dev` | `Seller@123` | Shop "Laptop Minh Châu", `seller_status = APPROVED` |
| `SELLER` | `shop.audio@marketplace.dev` | `Seller@123` | Shop "Âm Thanh Việt", `seller_status = APPROVED` |
| `SELLER` | `shop.wearable@marketplace.dev` | `Seller@123` | Shop "Đồng Hồ Thông Minh Huế", `seller_status = APPROVED` |
| `SELLER` | `shop.smarthome@marketplace.dev` | `Seller@123` | Shop "Nhà Thông Minh 247", `seller_status = APPROVED` |
| `SELLER` | `shop.camera@marketplace.dev` | `Seller@123` | Shop "Góc Máy Ảnh Sài Gòn", `seller_status = APPROVED` |
| `SELLER` | `shop.accessories@marketplace.dev` | `Seller@123` | Shop "Phụ Kiện Số Cần Thơ", `seller_status = APPROVED` |
| `SELLER` | `shop.sneaker@marketplace.dev` | `Seller@123` | Shop "Sneaker Phố Cổ", `seller_status = APPROVED` |
| `SELLER` | `shop.pending@marketplace.dev` | `Seller@123` | Shop "Shop Mới Chờ Duyệt", `seller_status = PENDING`; no products, for admin approve/suspend tests |
| `SELLER` | `shop.suspended@marketplace.dev` | `Seller@123` | Shop "Shop Đang Tạm Khóa", `seller_status = SUSPENDED`; no products, for admin approve/suspend tests |
| `ADMIN` | `admin@marketplace.dev` | `Admin@123` | Development only — see below |

Passwords are hashed with BCrypt using the `pgcrypto` extension that `V1` already installs:
`crypt(password, gen_salt('bf', 10))` produces `$2a$10$...`, the same format
`BCryptPasswordEncoder.matches()` verifies. Because this file is committed, the credentials are
public: rotate or delete the three accounts before pointing anything beyond local development at the
database.

The `ADMIN` row exists so the three roles can be exercised locally. Production admin accounts must be
created through an operational channel — never through public registration.

## Data

| Category | Products |
|---|---|
| Điện thoại | 4 |
| Laptop | 4 |
| Máy tính bảng | 3 |
| Tai nghe & Loa | 4 |
| Đồng hồ thông minh | 4 |
| Gia dụng thông minh | 5 |
| Máy ảnh & Flycam | 5 |
| Phụ kiện công nghệ | 3 |
| Thể thao & Thời trang | 4 |

Shop names are invented, not real stores. Products per shop: Trần Minh Khoa (Apple: 8), Android Hub
Đà Nẵng (3), Laptop Minh Châu (4), Âm Thanh Việt (3), Đồng Hồ Thông Minh Huế (2), Nhà Thông Minh 247
(5), Góc Máy Ảnh Sài Gòn (5), Phụ Kiện Số Cần Thơ (2), Sneaker Phố Cổ (4).

All 36 products are `active` and have an `image_url`, so they satisfy
`ck_products_active_has_image`. `rating_average` and `review_count` stay `0` on purpose: the seed
does not invent reviews that do not exist in `reviews`.

### Carriers

Admin-managed delivery companies (table `carriers`, `V16`). A seller picks an active one for
`PLATFORM_DELIVERY`. Names are real Vietnamese carriers used only for the demo; the project does not
call their APIs.

| Carrier | Active |
|---|---|
| Giao Hàng Nhanh (GHN) | yes |
| Giao Hàng Tiết Kiệm (GHTK) | yes |
| Viettel Post | yes |
| J&T Express | yes |
| Ninja Van | no (admin sees it, sellers do not) |

### Vouchers

Platform vouchers applied at checkout with `voucherCode`. Validity windows are computed from `now()`
when the seed runs, so re-running the seed refreshes them.

| Code | Discount | Window | Uses | What it tests |
|---|---|---|---|---|
| `WELCOME10` | 10% | active, 90 days | 0 / 1000 | Normal checkout with a discount |
| `SALE20` | 20% | active, 30 days | 0 / 100 | Normal checkout with a discount |
| `FLASH50` | 50% | active, 7 days | 0 / 1 | One checkout uses it up; the next gets `409 VOUCHER_EXHAUSTED` |
| `SOLDOUT15` | 15% | active, 30 days | 5 / 5 | `409 VOUCHER_EXHAUSTED` right away |
| `EXPIRED30` | 30% | ended yesterday | 0 / 100 | `409 VOUCHER_EXPIRED` |
| `UPCOMING25` | 25% | starts in 7 days | 0 / 100 | `409 VOUCHER_EXPIRED` (not started yet) |

`SOLDOUT15` has `times_used = 5` without real orders behind it; it exists only to test the exhausted
case. On a re-run the seed keeps `times_used` and `discount_percent` (the API forbids changing the
percent once a voucher was used) and never lowers `usage_limit` below `times_used`.

### Sources

Product data is real: current models, real specifications and reference prices from Vietnamese
retailers checked in October 2026 (CellphoneS, Hoàng Hà Mobile, FPT Shop, Thế Giới Di Động,
T&T Center, plus manufacturer pages such as `apple.com/vn`, `samsung.com/vn`, `dyson.vn`).

Product images are hotlinked from Wikimedia Commons, and every image URL was verified to return
HTTP 200 with an image content type before being committed. All of them are free content (commonly
CC BY, CC BY-SA, CC0 or public domain — the file page states the exact licence and author). Every
file page follows `https://commons.wikimedia.org/wiki/File:<name with underscores>`, so the files used
by the seed are:

```text
iPhone 16 Pro Max 256GB        File:IPhone 16 Pro Max Desert Titanium Rear.png
iPhone 15 Pro 128GB            File:Apple iPhone 15 Pro.jpg
Samsung Galaxy S25 Ultra       File:Samsung Galaxy S25 Ultra.jpg
Google Pixel 8 Pro             File:Google Pixel 8 Pro.jpg
MacBook Air M2                 File:M2 Macbook Air Starlight model.jpg
MacBook Pro 14 inch M3         File:M3 Macbook Pro 14 inch Space Grey model.jpg
Lenovo ThinkPad X1 Carbon      File:Lenovo ThinkPad X1 Carbon 014 (10774123843).jpg
ASUS ROG Zephyrus G14          File:ASUS ROG Zephyrus 2026-08-15 G14.jpg
iPad Air 11 inch M2            File:IPad Air.png
iPad Pro 11 inch M4            File:M4 iPad Pro series - 1.jpg
Samsung Galaxy Tab S9          File:Samsung Galaxy Tab S9.png
Apple AirPods Pro 2            File:AirPods Pro (2nd generation).jpg
Apple AirPods Max              File:Apple airpods max 1.jpg
Bose SoundLink Micro           File:Bose SoundLink Micro.jpg
JBL Charge 4                   File:JBL Charge 4 1v2.jpg
Apple Watch Series 9           File:Apple Watch Series 9 1 2023-11-14.jpg
Apple Watch Ultra 2            File:Apple Watch Ultra 2.jpg
Garmin Forerunner 955 Solar    File:Garmin 955 Solar.jpg
Casio G-Shock GW-M5610U        File:CASIO G-Shock GW-M5610U.jpg
Xiaomi Mi Robot Vacuum-Mop     File:XiaomiMiRobotVacuumCleanerRobot.jpg
iRobot Roomba i7+              File:IRobot Roomba i7+.jpg
Nespresso Vertuo Pop           File:Nespresso Vertuo Pop.jpg
KitchenAid Artisan 5KSM150     File:Red KitchenAid Artisan.jpg
Dyson Supersonic               File:Dyson Supersonic Hair Dryer 3 2017-01-28.jpg
Sony Alpha 7R IV               File:Sony Alpha 7R IV body with Sony Zeiss 55mm lens.jpg
Canon EOS R6                   File:Canon R6 und RF 85 1,2-8068.jpg
Fujifilm X-T4                  File:Fujifilm X-T4 29 apr 2020a.jpg
DJI Mini 4 Pro                 File:2024 Dron DJI Mini 4 Pro (01).jpg
GoPro HERO9 Black              File:GoPro Hero 9 Black - Front.jpg
Logitech MX Master 3S          File:Logitech MX Master 3S HS11.jpg
SanDisk Extreme SSD 1TB        File:SanDisk Extreme Portable SSD 1 TB - front view.jpg
Apple TV 4K 64GB               File:Apple TV 4K (3rd Generation, 2022) with Siri Remote.jpg
Nike Air Max 90                File:Nike Air Max 90.jpg
Adidas Superstar               File:Adidas Superstar shoes pair.jpg
Dr. Martens 1460               File:Dr. Martens boots. Made in England.jpeg
Fjallraven Kanken Classic      File:Fjallraven Kanken backpack (Unsplash).jpg
```

## How to run

From `backend/`:

```powershell
# Uses DB_URL / DB_USERNAME / DB_PASSWORD from backend/.env
# (asks for confirmation when the target is not localhost)
.\seed\apply-seed.ps1

# Same, without the confirmation prompt (for example in a script)
.\seed\apply-seed.ps1 -Force

# Another configuration file
.\seed\apply-seed.ps1 -EnvFile .env.supabase -Force
```

Because it reads the same variables the application uses, the seed follows `DB_URL` wherever it
points: the PostgreSQL service from Compose, or Supabase/Neon.

The script never prints the password and never puts it on the command line: it writes a temporary
env file for `psql` and deletes it afterwards. `psql` runs inside the `pgvector/pgvector:pg16` image,
so a `localhost` in `DB_URL` is rewritten to `host.docker.internal` (a container cannot reach the host
through `localhost`).

At the end it prints a summary:

```text
   role   | accounts
----------+----------
 ADMIN    |        1
 CUSTOMER |        1
 SELLER   |       11
```

Without Docker you can apply the same file with any `psql`, or paste it into the Supabase SQL editor:

```powershell
psql "postgresql://<user>:<password>@<host>:5432/<db>?sslmode=require" -v ON_ERROR_STOP=1 -f seed\seed-dev.sql
```

## Re-running and cleanup

- Re-running is safe: rows are matched by fixed UUID and updated, never duplicated. The whole file
  runs in a single transaction.
- If one of the seed emails already belongs to a different row, the `uq_users_email` index
  rejects the insert. Delete that row first or edit the email in the seed.
- Deleting the seeded users fails while other rows still reference them (`ON DELETE RESTRICT`) — that
  is intended, not a bug. The snippet below removes the minimum for a database that only contains
  seed data; if it also has orders, order items, shipments, payments or chat conversations, delete
  those rows first (in dependency order), because they reference users and products as well:

```sql
DELETE FROM vouchers   WHERE code IN ('WELCOME10','SALE20','FLASH50','SOLDOUT15','EXPIRED30','UPCOMING25');
DELETE FROM carriers   WHERE id::text LIKE '55555555-5555-4555-8555-%';
DELETE FROM cart_items WHERE product_id IN (SELECT id FROM products WHERE id::text LIKE '33333333-3333-4333-8333-%');
DELETE FROM reviews    WHERE product_id IN (SELECT id FROM products WHERE id::text LIKE '33333333-3333-4333-8333-%');
DELETE FROM products   WHERE id::text LIKE '33333333-3333-4333-8333-%';
DELETE FROM categories WHERE id::text LIKE '22222222-2222-4222-8222-%';
DELETE FROM users      WHERE id::text LIKE '11111111-1111-4111-8111-%';
```
