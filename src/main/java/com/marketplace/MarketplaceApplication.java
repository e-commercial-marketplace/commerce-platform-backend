package com.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Điểm khởi động duy nhất của Spring Boot modular monolith.
 *
 * <p>{@code @SpringBootApplication} gộp ba vai trò: {@code @Configuration} để khai báo bean,
 * {@code @EnableAutoConfiguration} để cấu hình theo dependency và {@code @ComponentScan} để quét
 * bean từ package {@code com.marketplace} trở xuống.
 *
 * <p>Theo PLAN, auth, catalog, cart, order, payment, shipment, chat và ai là các feature cùng chạy
 * trong một JVM/deployment unit. Các feature gọi nhau bằng Spring bean method call, không qua HTTP
 * nội bộ hay service discovery.
 *
 * <p>Tắt {@link UserDetailsServiceAutoConfiguration}: xác thực đi qua JWT của feature auth, nên
 * không cần user in-memory mặc định với mật khẩu sinh ngẫu nhiên in ra log.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MarketplaceApplication {

  /**
   * Bootstrap Spring context và chạy embedded web server.
   *
   * <p>Chạy local từ thư mục {@code backend} bằng {@code mvn spring-boot:run} hoặc chạy jar đã
   * build. Database/profile được đọc từ các file {@code application*.yml} và biến môi trường.
   *
   * @param args tham số dòng lệnh được Spring Boot xử lý
   */
  public static void main(String[] args) {
    SpringApplication.run(MarketplaceApplication.class, args);
  }
}
