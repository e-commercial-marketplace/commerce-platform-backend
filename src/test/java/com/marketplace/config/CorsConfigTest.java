package com.marketplace.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** CORS mặc định tắt và chỉ bật cho origin được khai báo trong {@code app.cors.allowed-origin-patterns}. */
class CorsConfigTest {

  private static MockHttpServletRequestBuilder preflight(String origin) {
    return options("/api/v1/products")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization");
  }

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  class WhenNotConfigured {

    @Autowired private MockMvc mockMvc;

    @Test
    void preflightIsRejected() throws Exception {
      mockMvc
          .perform(preflight("http://evil.example"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
  }

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("test")
  @TestPropertySource(properties = "app.cors.allowed-origin-patterns=http://localhost:3000")
  class WhenConfigured {

    @Autowired private MockMvc mockMvc;

    @Test
    void allowedOriginGetsPreflightApproval() throws Exception {
      mockMvc
          .perform(preflight("http://localhost:3000"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void otherOriginIsStillRejected() throws Exception {
      mockMvc
          .perform(preflight("http://evil.example"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
  }
}
