package com.marketplace.service;

import com.marketplace.dto.request.ForgotPasswordRequest;
import com.marketplace.dto.request.ResetPasswordRequest;

/** Quên mật khẩu: xin mã qua email, rồi đặt mật khẩu mới bằng mã đó. */
public interface PasswordResetService {

  /**
   * Cấp và gửi mã nếu email thuộc tài khoản đang hoạt động. Không bao giờ báo cho client biết email
   * có tồn tại hay không: mọi trường hợp đều kết thúc bình thường.
   */
  void sendResetCode(ForgotPasswordRequest request);

  /**
   * Đặt mật khẩu mới bằng mã. Thành công thì thu hồi mọi refresh token của user.
   *
   * @throws com.marketplace.common.exception.BusinessException 400 khi mã sai, hết hạn, đã dùng,
   *     hết lượt thử, hoặc email không có mã hợp lệ (cùng một thông báo)
   */
  void resetPassword(ResetPasswordRequest request);
}
