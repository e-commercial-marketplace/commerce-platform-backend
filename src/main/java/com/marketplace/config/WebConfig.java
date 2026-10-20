package com.marketplace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình chung cho tầng web: thêm prefix version cho toàn bộ REST controller.
 *
 * <p>Prefix đọc từ {@code api.prefix} trong {@code application.yml}; nhờ vậy controller khai báo
 * route nghiệp vụ không kèm version, ví dụ {@code @RequestMapping("/products")}, còn client gọi
 * {@code /api/v1/products}. Khi cần version mới, tạo controller/handler riêng cho version đó thay vì
 * đổi giá trị dùng chung, để URL cũ vẫn phục vụ được.
 *
 * <p>Không đặt {@code server.servlet.context-path} cho việc version hóa API: cấu hình đó đổi đường
 * dẫn của cả ứng dụng, gồm actuator và Swagger. Ở đây chỉ controller của ứng dụng bị thêm prefix,
 * còn {@code /actuator/**}, {@code /swagger-ui/**}, {@code /v3/api-docs} và {@code /error} giữ
 * nguyên đường dẫn gốc.
 *
 * <p>Security matcher trong {@code SecurityConfig} và tài liệu {@code docs/API_CONTRACT.md} phải
 * khớp prefix này.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /** Package gốc của ứng dụng: chỉ controller trong đây mới được thêm prefix version. */
  private static final String APPLICATION_BASE_PACKAGE = "com.marketplace";

  private final String apiPrefix;

  /**
   * @param apiPrefix prefix version dùng chung, ví dụ {@code /api/v1}
   */
  public WebConfig(@Value("${api.prefix}") String apiPrefix) {
    this.apiPrefix = apiPrefix;
  }

  /**
   * Thêm prefix version cho handler method khai báo trong REST controller của ứng dụng.
   *
   * @param configurer cấu hình path matching của Spring MVC
   */
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(apiPrefix, this::isApplicationRestController);
  }

  /**
   * Nhận diện handler cần thêm prefix: class gắn {@code @RestController} và nằm trong package ứng
   * dụng.
   *
   * <p>Không dùng {@code HandlerTypePredicate} của Spring cho việc này vì nó ghép các điều kiện theo
   * kiểu OR (khớp package hoặc khớp annotation); ở đây cần cả hai điều kiện cùng đúng để controller
   * nội bộ của thư viện như springdoc và {@code BasicErrorController} không bị đổi đường dẫn.
   *
   * @param handlerType kiểu bean đang được đăng ký handler method
   * @return {@code true} nếu đường dẫn của handler cần được thêm prefix
   */
  private boolean isApplicationRestController(Class<?> handlerType) {
    return AnnotatedElementUtils.hasAnnotation(handlerType, RestController.class)
        && isInApplicationPackage(handlerType);
  }

  /**
   * Kiểm tra class thuộc package ứng dụng hoặc package con của nó.
   *
   * @param handlerType kiểu bean cần kiểm tra
   * @return {@code true} nếu nằm trong {@value #APPLICATION_BASE_PACKAGE} hoặc package con
   */
  private boolean isInApplicationPackage(Class<?> handlerType) {
    String packageName = ClassUtils.getPackageName(handlerType);
    return packageName.equals(APPLICATION_BASE_PACKAGE)
        || packageName.startsWith(APPLICATION_BASE_PACKAGE + ".");
  }
}
