package com.marketplace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình metadata cho tài liệu OpenAPI của modular monolith.
 *
 * <p>Springdoc tự đọc bean {@link OpenAPI} này và dùng nó để tạo OpenAPI JSON cùng giao diện
 * Swagger UI. Class này chỉ mô tả tài liệu; nó không tạo endpoint nghiệp vụ và không thay thế
 * việc kiểm tra quyền trong {@code SecurityConfig}.
 *
 * <p>Trong môi trường local, xem tài liệu tại {@code /swagger-ui.html} hoặc JSON tại
 * {@code /v3/api-docs}. Khi thêm controller ở các feature như {@code catalog}, {@code order} hay
 * {@code payment}, Springdoc sẽ tự quét annotation của controller đó.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Đăng ký metadata dùng chung cho toàn bộ API.
   *
   * @return bean OpenAPI được Springdoc dùng để render tài liệu
   */
  @Bean
  public OpenAPI marketplaceOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Marketplace API")
                .version("0.0.1")
                .description("E-Commerce Marketplace Modular Monolith"));
  }
}
