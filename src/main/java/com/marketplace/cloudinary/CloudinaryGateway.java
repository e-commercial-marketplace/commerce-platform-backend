package com.marketplace.cloudinary;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.config.CloudinaryProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ký yêu cầu upload lên Cloudinary và kiểm tra URL ảnh có thuộc tài khoản của dự án không.
 *
 * <p>Cách ký của Cloudinary: sắp các tham số theo tên, nối thành {@code a=1&b=2}, thêm API secret vào cuối
 * (không có dấu nối) rồi băm SHA-1. Client phải gửi đúng các tham số đã ký, sửa một giá trị là chữ ký sai.
 */
@Component
@RequiredArgsConstructor
public class CloudinaryGateway {

  /** Định dạng ảnh được nhận; Cloudinary tự từ chối file khác. */
  public static final String ALLOWED_FORMATS = "jpg,png,webp";

  private static final String IMAGE_HOST = "res.cloudinary.com";

  private final CloudinaryProperties properties;

  /** Đủ 3 biến cấu hình thì mới bật upload. */
  public boolean isConfigured() {
    return StringUtils.hasText(properties.cloudName())
        && StringUtils.hasText(properties.apiKey())
        && StringUtils.hasText(properties.apiSecret());
  }

  public String apiKey() {
    return properties.apiKey();
  }

  /** Địa chỉ client gửi file lên. */
  public String uploadUrl() {
    return "https://api.cloudinary.com/v1_1/" + properties.cloudName() + "/image/upload";
  }

  /**
   * Chữ ký cho một lần upload ảnh. {@code overwrite=false} nằm trong chuỗi ký nên client không ghi đè được ảnh đã
   * có; chữ ký có hiệu lực một giờ kể từ {@code timestamp} (Cloudinary tự kiểm tra).
   */
  public String signUpload(String publicId, long timestamp) {
    Map<String, String> params = new TreeMap<>();
    params.put("allowed_formats", ALLOWED_FORMATS);
    params.put("overwrite", "false");
    params.put("public_id", publicId);
    params.put("timestamp", Long.toString(timestamp));
    String toSign =
        params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
    return sha1Hex(toSign + properties.apiSecret());
  }

  /**
   * Từ chối URL ảnh không thuộc Cloudinary của dự án, để không ai nhét link ngoài vào app của người khác. Chưa cấu
   * hình Cloudinary thì bỏ qua (chạy local, test).
   */
  public void requireOwnImage(String imageUrl) {
    if (isConfigured() && !isOwnImage(imageUrl)) {
      throw BusinessException.validation("imageUrl must be an image uploaded to this marketplace");
    }
  }

  /** So từng phần của URL thay vì so chuỗi đầu, để {@code res.cloudinary.com.evil.com} không qua được. */
  private boolean isOwnImage(String imageUrl) {
    try {
      URI uri = new URI(imageUrl.trim());
      return "https".equals(uri.getScheme())
          && IMAGE_HOST.equals(uri.getHost())
          && uri.getPort() == -1
          && uri.getUserInfo() == null
          && uri.getPath() != null
          && uri.getPath().startsWith("/" + properties.cloudName() + "/image/upload/")
          && !uri.getPath().contains("..");
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String sha1Hex(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is always available in the JDK", e);
    }
  }
}
