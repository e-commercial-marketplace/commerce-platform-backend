package com.marketplace.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marketplace.config.AppMailProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gửi mã qua Brevo: dùng server giả, không gọi Brevo thật. */
class EmailServiceImplTest {

  private static final String URL = "https://api.brevo.com/v3/smtp/email";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  private EmailServiceImpl service(boolean enabled) {
    return new EmailServiceImpl(builder.build(), new AppMailProperties(enabled, "shop@test.dev", "test-key"));
  }

  @Test
  void sendsTheCodeThroughBrevo() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", "test-key"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.sender.email").value("shop@test.dev"))
        .andExpect(jsonPath("$.to[0].email").value("user@test.dev"))
        .andExpect(jsonPath("$.subject").value("Mã đặt lại mật khẩu"))
        .andExpect(jsonPath("$.textContent").value(Matchers.containsString("012345")))
        .andExpect(jsonPath("$.textContent").value(Matchers.containsString("15 phút")))
        .andRespond(withSuccess("{\"messageId\":\"m1\"}", MediaType.APPLICATION_JSON));

    service(true).sendPasswordResetCode("user@test.dev", "012345", 15);

    server.verify();
  }

  @Test
  void callsNothingWhenEmailIsDisabled() {
    service(false).sendPasswordResetCode("user@test.dev", "012345", 15);

    server.verify(); // không khai báo request nào: có gọi HTTP là test hỏng
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 500})
  void aBrevoRejectionIsThrownToTheCaller(int status) {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

    assertThatThrownBy(() -> service(true).sendPasswordResetCode("user@test.dev", "012345", 15))
        .isInstanceOf(RestClientException.class);
  }
}
