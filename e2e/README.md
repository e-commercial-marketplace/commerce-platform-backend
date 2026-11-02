# E2E API test (Postman)

Chạy các hành trình chính vào **backend thật** (server HTTP thật, Postgres thật, migration Flyway thật).
Bổ sung cho test MockMvc trong `src/test` (chạy trên H2), không thay thế.

> Các hành trình dùng URL ảnh `example.com`. Khi chạy E2E, để trống `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`
> và `CLOUDINARY_API_SECRET` trong `.env`, nếu không các URL này sẽ bị từ chối với lỗi `400`.

## Bao phủ

| Thư mục | Hành trình |
|---|---|
| 0. Admin login | Đăng nhập admin có sẵn |
| 1. Auth journey | Đăng ký customer, sai mật khẩu 401, đăng nhập, `/users/me`, refresh xoay vòng, dùng lại refresh cũ 401, customer gọi API admin 403, không token 401 |
| 2. Admin carriers | Thêm đơn vị vận chuyển, trùng tên (không phân biệt hoa thường) 400, thêm và tắt một đơn vị |
| 3. Seller onboarding | Đăng ký seller (PENDING) bị chặn 403, admin duyệt, admin tạo category, seller tạo sản phẩm (kho 5), seller chỉ thấy đơn vị đang hoạt động |
| 4. COD purchase to COMPLETED | Giỏ 2 cái, checkout COD, kho còn 3, seller xác nhận, shipment với đơn vị đã tắt 400, tạo shipment, huỷ dòng đã gán shipment 409, PREPARING, SHIPPED thiếu mã 400, SHIPPED, DELIVERED, order DELIVERED và COD đã thu, khách xác nhận nhận hàng thành COMPLETED, xác nhận lại 400 |
| 5. Seller cancellation | Checkout 1 cái, seller huỷ dòng, order CANCELLED và payment FAILED, kho về 3, logout, refresh sau logout 401 |
| 6. Voucher at checkout | Admin tạo voucher 10% một lượt, khách kiểm tra mã (chữ thường), checkout có voucher, mã hết lượt 409 |
| 7. Cleanup | Seller ẩn sản phẩm E2E (không xoá), trang công khai của sản phẩm trả 404 |

Mỗi lần chạy tự tạo customer, seller, category, sản phẩm và đơn vị vận chuyển mới (tên có dấu thời
gian), nên chạy lại nhiều lần không xung đột. Bước cuối ẩn sản phẩm vừa tạo, để DB local không đầy
sản phẩm "E2E Phone" đang bán (chúng làm sai kết quả tìm kiếm và trợ lý AI). Chỉ cần sẵn **một tài khoản ADMIN**.

Không bao phủ: VNPAY (cần trang thanh toán sandbox, đã có test chữ ký trong `PaymentApiTest`), quên mật
khẩu qua email, job nền (24 giờ, tự hoàn tất 3 ngày).

## Chạy

1. Bật stack local (trong thư mục `backend`):
   ```powershell
   docker compose up -d --build
   ```
   Chờ `docker compose ps` báo `marketplace-backend` là `healthy`.
2. Cần một admin: nạp seed (có `admin@marketplace.dev`) theo hướng dẫn trong `seed/`, hoặc dùng admin
   tạo từ `APP_ADMIN_EMAIL` trong `.env` (khi đó truyền thêm `--env-var "adminEmail=..."`).
3. Chạy collection, truyền mật khẩu admin lúc chạy (không lưu trong file):
   ```powershell
   postman collection run e2e/marketplace.postman_collection.json -e e2e/local.postman_environment.json --env-var "adminPassword=<mật khẩu admin>"
   ```
   Dòng "Postman credentials are required for publishing run details" chỉ là thông báo: kết quả vẫn
   chạy và in ở máy, không cần `postman login`.

Muốn xem bằng giao diện: import hai file `.json` vào Postman app, chọn environment `marketplace-local`,
điền `adminPassword`, rồi chạy bằng Collection Runner.

## Lưu ý

- **Chỉ chạy vào môi trường local.** Collection tạo dữ liệu thật (user, sản phẩm, order) ở mỗi lần chạy.
  Đừng đổi `baseUrl` sang Render hoặc Supabase.
- Các request phụ thuộc nhau theo thứ tự (id truyền qua collection variables): chạy cả collection, đừng
  chạy lẻ từng request.
