# Điều khoản và chính sách dành cho khách hàng

Nguồn: tổng hợp từ docs (PRD, SRS, features). Mỗi mục "##" là một đoạn độc lập dùng cho RAG.
Không dùng file này cho giá, tồn kho, trạng thái đơn hoặc voucher cụ thể: các dữ liệu đó lấy trực tiếp từ hệ thống.

## Giới thiệu sàn
Chủ đề: chung
Marketplace là sàn thương mại điện tử nhiều người bán. Khách có thể mua sản phẩm của nhiều shop trong cùng
một đơn. Khi chưa đăng nhập, khách vẫn xem, tìm kiếm sản phẩm và hỏi trợ lý AI về sản phẩm và chính sách.
Để dùng giỏ hàng, đặt hàng, thanh toán, theo dõi giao hàng và đánh giá sản phẩm, khách cần đăng nhập bằng
tài khoản khách hàng.

## Đăng ký và đăng nhập
Chủ đề: tài khoản
Khách tạo tài khoản khách hàng bằng email và mật khẩu, sau đó đăng nhập bằng email và mật khẩu đó. Khách có
thể đăng xuất bất cứ lúc nào. Tài khoản bị khóa thì không đăng nhập được.

## Đổi và quên mật khẩu
Chủ đề: tài khoản
Khách đã đăng nhập đổi mật khẩu bằng cách nhập mật khẩu hiện tại và mật khẩu mới. Khi quên mật khẩu, khách
nhập email để nhận mã 6 chữ số qua email. Mã chỉ dùng được một lần, hết hạn sau 15 phút và cho nhập sai tối
đa 5 lần. Sau khi đổi hoặc đặt lại mật khẩu, các thiết bị đang đăng nhập khác sẽ phải đăng nhập lại.

## Hồ sơ cá nhân
Chủ đề: tài khoản
Khách xem và cập nhật tên, số điện thoại và địa chỉ của mình trong phần tài khoản. Số điện thoại phải là số
di động Việt Nam hợp lệ.

## Sản phẩm và giá
Chủ đề: sản phẩm
Toàn bộ giá trên sàn dùng đồng Việt Nam (VND). Sản phẩm đã bị shop ngừng bán sẽ không hiển thị và không mua
được. Giá trong giỏ hàng luôn được tính theo giá hiện tại của sản phẩm. Giá và tên sản phẩm trong đơn được
chốt tại thời điểm đặt hàng và không thay đổi dù shop sửa sản phẩm sau đó.

## Giỏ hàng
Chủ đề: giỏ hàng
Cần đăng nhập bằng tài khoản khách hàng để dùng giỏ hàng. Mỗi sản phẩm chỉ có một dòng trong giỏ, số lượng ít
nhất là 1 và không vượt quá số hàng còn lại. Sản phẩm hết hàng hoặc đã ngừng bán không thể đặt mua. Tổng tiền
giỏ hàng do hệ thống tính.

## Cách đặt hàng
Chủ đề: đặt hàng
Thêm sản phẩm vào giỏ, chọn các sản phẩm muốn mua, nhập địa chỉ giao hàng, chọn COD hoặc VNPAY rồi đặt hàng.
Một đơn có thể gồm sản phẩm của nhiều shop. Các sản phẩm không được chọn vẫn nằm lại trong giỏ. Địa chỉ giao
hàng được lưu theo đơn tại lúc đặt. Hiện phí vận chuyển là 0 ₫ và mỗi đơn dùng tối đa một voucher.

## Thanh toán khi nhận hàng (COD)
Chủ đề: thanh toán
Với COD, khách trả tiền khi nhận hàng. Đơn COD không có hạn thanh toán 24 giờ như VNPAY. Thanh toán COD chỉ
được ghi nhận hoàn tất khi tất cả phần hàng của các shop trong đơn (không bị hủy) đều đã giao thành công.

## Thanh toán online qua VNPAY
Chủ đề: thanh toán
Đơn chọn VNPAY phải thanh toán trong vòng 24 giờ kể từ lúc đặt; quá hạn, đơn tự hủy. Nếu một lần thanh toán
VNPAY thất bại, đơn vẫn còn nguyên và khách có thể thanh toán lại. Mỗi giao dịch thành công chỉ được ghi nhận
một lần. Shop chỉ bắt đầu chuẩn bị hàng cho đơn VNPAY sau khi đơn đã được thanh toán.

## Hủy đơn và hủy sản phẩm trong đơn
Chủ đề: hủy đơn
Shop có thể hủy từng sản phẩm trong đơn khi sản phẩm đó chưa được đóng vào kiện giao. Kiện hàng đang chờ hoặc
đang chuẩn bị có thể bị hủy cả kiện. Không thể hủy sau khi hàng đã được gửi đi. Đơn VNPAY đã thanh toán thì
chưa hủy được vì sàn chưa có quy trình hoàn tiền. Khi một sản phẩm bị hủy trước lúc thanh toán xong, tổng tiền
đơn được tính lại; nếu mọi sản phẩm đều bị hủy, đơn chuyển sang đã hủy và khách không phải trả tiền COD. Việc
hủy hàng của shop này không ảnh hưởng đến hàng của shop khác trong cùng đơn.

## Giao hàng
Chủ đề: giao hàng
Mỗi shop tự giao phần hàng của mình trong đơn, qua đơn vị vận chuyển của sàn hoặc tự giao. Vì vậy một đơn
nhiều shop có thể có nhiều kiện hàng, mỗi kiện chỉ chứa hàng của một shop. Kiện hàng đi qua các trạng thái:
chờ xử lý, đang chuẩn bị, đã gửi (có mã vận đơn), đã giao, giao thất bại hoặc đã hoàn về. Khách theo dõi trạng
thái từng kiện trong chi tiết đơn. Đơn chỉ được coi là đã giao khi mọi kiện cần giao đều đã giao.

## Xác nhận đã nhận hàng
Chủ đề: giao hàng
Khi đơn đã giao đủ, khách bấm "Đã nhận hàng" để hoàn tất đơn. Nếu khách không bấm, đơn tự hoàn tất sau 3 ngày
kể từ lần giao cuối cùng. Chỉ đơn đã hoàn tất mới cho phép đánh giá sản phẩm.

## Giao hàng thất bại và hàng hoàn về
Chủ đề: đổi trả
Mỗi kiện hàng có kết quả riêng: đã giao, giao thất bại hoặc đã hoàn về shop. Trong một đơn nhiều shop, có thể
kiện của shop này giao thành công trong khi kiện của shop khác giao thất bại; khi đó khách xem kết quả theo
từng kiện trong chi tiết đơn. Hiện sàn chưa hỗ trợ thanh toán COD từng phần cho trường hợp kết quả lẫn lộn.

## Hoàn tiền
Chủ đề: đổi trả
Hiện sàn chưa có quy trình hoàn tiền tự động cho khách. Các trường hợp cần hoàn tiền được sàn xem xét và xử
lý riêng. Trợ lý AI không thể hoàn tiền hay hủy đơn thay khách.

## Voucher
Chủ đề: voucher
Khách nhập mã voucher ở bước đặt hàng; mỗi đơn dùng tối đa một voucher. Voucher giảm theo phần trăm trên tổng
tiền các sản phẩm chưa bị hủy trong đơn và được tính lại nếu có sản phẩm bị hủy. Mỗi mã có thời hạn và số lượt
sử dụng; mã hết hạn hoặc hết lượt sẽ bị từ chối. Lượt đã dùng không được trả lại khi đơn bị hủy.

## Đánh giá sản phẩm
Chủ đề: đánh giá
Khách chỉ đánh giá được sản phẩm đã mua và đã nhận: cần có đơn đã hoàn tất chứa sản phẩm đó (không bị hủy).
Mỗi khách đánh giá mỗi sản phẩm một lần, gồm số sao từ 1 đến 5 và nhận xét. Hiện chưa hỗ trợ ảnh hay thẻ đánh giá.

## Trợ lý AI
Chủ đề: trợ lý AI
Trợ lý AI giúp tìm sản phẩm và trả lời về chính sách mua hàng của sàn. Khi chưa đăng nhập, khách chỉ hỏi được
về sản phẩm và chính sách. Trợ lý không đặt, hủy hay sửa đơn, không thanh toán hay hoàn tiền; các việc đó
khách thực hiện trong ứng dụng. Lịch sử trò chuyện chỉ được lưu khi khách đã đăng nhập. Số câu hỏi mỗi phút
có giới hạn.
