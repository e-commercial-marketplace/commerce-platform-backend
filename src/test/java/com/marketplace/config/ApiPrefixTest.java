package com.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Kiểm tra prefix version {@code /api/v1} được thêm tập trung bởi {@link WebConfig}.
 *
 * <p>Test quan sát hai phía: bảng mapping của Spring MVC (route khai báo {@code /products/...} phải
 * được expose ở {@code /api/v1/products/...}) và hành vi HTTP thật qua {@code MockMvc}, bao gồm việc
 * actuator cùng error controller của Spring Boot không bị đổi đường dẫn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiPrefixTest {

  @Autowired private MockMvc mockMvc;

  // Chỉ định tường minh bean `requestMappingHandlerMapping` của WebMvc; actuator dùng handler
  // mapping riêng cho /actuator/**.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

  /** Route của controller phải nằm dưới prefix, không còn ở đường dẫn trần. */
  @Test
  void controllerMappingIsExposedUnderVersionPrefix() {
    Set<String> patterns = mappedPatterns();

    assertThat(patterns).contains("/api/v1/products/prefix-probe");
    assertThat(patterns).doesNotContain("/products/prefix-probe");
  }

  /** URL có prefix phải gọi được thật qua HTTP, không chỉ xuất hiện trong bảng mapping. */
  @Test
  void prefixedRouteIsReachableThroughHttp() throws Exception {
    mockMvc
        .perform(get("/api/v1/products/prefix-probe"))
        .andExpect(status().isOk())
        .andExpect(content().string("prefix-probe-ok"));
  }

  /**
   * Endpoint không phải {@code @RestController} của ứng dụng không được thêm prefix: actuator giữ
   * {@code /actuator/health} và error controller giữ {@code /error}. Nếu mất hai đường dẫn này,
   * healthcheck của Docker và luồng báo lỗi của Spring Boot sẽ hỏng.
   */
  @Test
  void actuatorAndErrorControllerKeepUnversionedPaths() throws Exception {
    Set<String> patterns = mappedPatterns();

    assertThat(patterns).contains("/error");
    assertThat(patterns).noneMatch(pattern -> pattern.startsWith("/api/v1/actuator"));

    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  /**
   * Route public dưới prefix nhưng chưa có controller (bổ sung ở Phase 1 trở đi) phải trả 404 theo
   * contract: security matcher đã cho qua nên request tới được DispatcherServlet, và
   * {@code GlobalExceptionHandler} phải map thành {@code RESOURCE_NOT_FOUND} thay vì 500.
   */
  @Test
  void unmappedPublicRouteUnderPrefixReturns404() throws Exception {
    mockMvc
        .perform(get("/api/v1/products"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  /** Gom toàn bộ path pattern đã đăng ký trong MVC handler mapping chuẩn của ứng dụng. */
  private Set<String> mappedPatterns() {
    Set<String> patterns = new HashSet<>();
    requestMappingHandlerMapping
        .getHandlerMethods()
        .keySet()
        .forEach(
            info -> {
              PathPatternsRequestCondition condition = info.getPathPatternsCondition();
              if (condition != null) {
                patterns.addAll(condition.getPatternValues());
              }
            });
    return patterns;
  }
}
