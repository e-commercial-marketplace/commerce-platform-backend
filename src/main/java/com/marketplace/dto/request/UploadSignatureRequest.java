package com.marketplace.dto.request;

import com.marketplace.enums.UploadPurpose;
import jakarta.validation.constraints.NotNull;

/** Body của {@code POST /uploads/signature}. */
public record UploadSignatureRequest(@NotNull UploadPurpose purpose) {}
