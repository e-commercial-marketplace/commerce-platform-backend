package com.marketplace.service.impl;

import com.marketplace.cloudinary.CloudinaryGateway;
import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.response.UploadSignatureResponse;
import com.marketplace.enums.Role;
import com.marketplace.enums.UploadPurpose;
import com.marketplace.repository.UserRepository;
import com.marketplace.security.AuthUser;
import com.marketplace.service.UploadService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

  private final CloudinaryGateway cloudinary;
  private final UserRepository userRepository;
  private final TimeProvider timeProvider;

  @Override
  public UploadSignatureResponse createSignature(AuthUser caller, UploadPurpose purpose) {
    // Bước 1: kiểm tra quyền trước, để người không có quyền không biết được cấu hình của server.
    String folder =
        switch (purpose) {
          case PRODUCT -> {
            userRepository.getApprovedSeller(caller.id()); // trạng thái duyệt đọc từ DB, không nằm trong token
            yield "products";
          }
          case CHAT -> {
            if (caller.role() != Role.CUSTOMER && caller.role() != Role.SELLER) {
              throw BusinessException.forbidden("Only chat participants can upload chat images");
            }
            yield "chat";
          }
        };
    // Bước 2: chưa cấu hình Cloudinary thì không ký được.
    if (!cloudinary.isConfigured()) {
      throw BusinessException.uploadNotConfigured("Image upload is not configured");
    }
    // Bước 3: backend tự sinh public id ngẫu nhiên, client không chọn được tên ảnh.
    String publicId = "marketplace/" + folder + "/" + UUID.randomUUID();
    long timestamp = timeProvider.now().getEpochSecond();
    return new UploadSignatureResponse(
        cloudinary.uploadUrl(),
        cloudinary.apiKey(),
        timestamp,
        publicId,
        CloudinaryGateway.ALLOWED_FORMATS,
        false,
        cloudinary.signUpload(publicId, timestamp));
  }
}
