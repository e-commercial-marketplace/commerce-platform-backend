package com.marketplace.service;

import com.marketplace.dto.response.UploadSignatureResponse;
import com.marketplace.enums.UploadPurpose;
import com.marketplace.security.AuthUser;

/** Upload ảnh theo features/upload/API_CONTRACT.md: file đi thẳng lên Cloudinary, backend chỉ cấp chữ ký. */
public interface UploadService {

  /** Cấp chữ ký upload cho {@code caller} nếu được phép upload ảnh cho {@code purpose}. */
  UploadSignatureResponse createSignature(AuthUser caller, UploadPurpose purpose);
}
