package com.marketplace.cloudinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.config.CloudinaryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Chữ ký Cloudinary và luật URL ảnh theo features/upload/API_CONTRACT.md. */
class CloudinaryGatewayTest {

  private final CloudinaryGateway gateway = new CloudinaryGateway(new CloudinaryProperties("demo", "key", "my-secret"));
  private final CloudinaryGateway off = new CloudinaryGateway(new CloudinaryProperties("", "", ""));

  @Test
  void signatureMatchesAValueComputedIndependently() {
    // SHA-1 của "allowed_formats=jpg,png,webp&overwrite=false&public_id=marketplace/products/abc&timestamp=1793600000"
    // nối với "my-secret", tính bằng công cụ khác (Python hashlib).
    assertThat(gateway.signUpload("marketplace/products/abc", 1793600000L))
        .isEqualTo("a763c061d810af73aa6379fe28cbe0e13f9cfe8c");
  }

  @Test
  void acceptsAnImageOfTheProjectCloud() {
    assertThatCode(() -> gateway.requireOwnImage("https://res.cloudinary.com/demo/image/upload/v1/marketplace/chat/a.jpg"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://res.cloudinary.com/demo/image/upload/a.jpg", // không phải https
        "https://res.cloudinary.com/other/image/upload/a.jpg", // cloud khác
        "https://res.cloudinary.com.evil.com/demo/image/upload/a.jpg", // tên miền giả
        "https://evil.com/res.cloudinary.com/demo/image/upload/a.jpg",
        "https://user@res.cloudinary.com/demo/image/upload/a.jpg",
        "https://res.cloudinary.com:8443/demo/image/upload/a.jpg",
        "https://res.cloudinary.com/demo/video/upload/a.mp4", // không phải ảnh
        "https://res.cloudinary.com/demo/image/upload/../../other/a.jpg",
        "not a url"
      })
  void rejectsEveryOtherUrl(String url) {
    assertThatThrownBy(() -> gateway.requireOwnImage(url)).isInstanceOf(BusinessException.class);
  }

  @Test
  void withoutConfigurationUrlsAreNotChecked() {
    assertThat(off.isConfigured()).isFalse();
    assertThatCode(() -> off.requireOwnImage("https://example.com/a.jpg")).doesNotThrowAnyException();
  }

  @Test
  void needsAllThreeSettings() {
    assertThat(gateway.isConfigured()).isTrue();
    assertThat(new CloudinaryGateway(new CloudinaryProperties("demo", "key", "")).isConfigured()).isFalse();
  }
}
