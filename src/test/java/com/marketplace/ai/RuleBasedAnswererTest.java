package com.marketplace.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.ai.rules.RuleBasedAnswerer;
import com.marketplace.ai.rules.RuleBasedAnswerer.RuleAnswer;
import com.marketplace.ai.tools.AiProductSearch;
import com.marketplace.ai.tools.ProductSearchTools;
import com.marketplace.dto.response.AiProductResponse;
import com.marketplace.entity.Category;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.support.TestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Bộ trả lời bằng luật và phần tìm product dùng chung với tool của LLM, chạy trên dữ liệu thật (H2). Tên
 * product có hậu tố ngẫu nhiên để không lẫn với dữ liệu của các test khác trong cùng database.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleBasedAnswererTest {

  @Autowired private RuleBasedAnswerer answerer;
  @Autowired private AiProductSearch productSearch;
  @Autowired private TestSupport support;
  @Autowired private CategoryRepository categories;

  private String tag; // hậu tố riêng của test này, ví dụ "qx3f9a"
  private Category phones;

  @BeforeEach
  void setUp() {
    tag = "q" + UUID.randomUUID().toString().substring(0, 6);
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    phones = categories.save(Category.builder().name("Điện thoại " + tag).build());
    Category other = support.category();
    support.product(seller, other, "Tai nghe " + tag + " Basic", "1500000.00", 5);
    support.product(seller, other, "Tai nghe " + tag + " Pro", "4500000.00", 5);
    support.product(seller, other, "Tai nghe " + tag + " Max", "9000000.00", 5);
    support.product(seller, phones, "Galaxy " + tag, "20000000.00", 5);
  }

  @Test
  void readsPriceLimitsInManyFormats() {
    assertThat(names(answerer.answer("tìm tai nghe " + tag + " dưới 5 triệu")))
        .containsExactlyInAnyOrder("Tai nghe " + tag + " Basic", "Tai nghe " + tag + " Pro");
    assertThat(names(answerer.answer("tai nghe " + tag + " dưới 2tr"))).containsExactly("Tai nghe " + tag + " Basic");
    assertThat(names(answerer.answer("tai nghe " + tag + " trên 5.000.000 đ"))).containsExactly("Tai nghe " + tag + " Max");
    assertThat(names(answerer.answer("tai nghe " + tag + " từ 1,5 triệu đến 5 triệu")))
        .containsExactlyInAnyOrder("Tai nghe " + tag + " Basic", "Tai nghe " + tag + " Pro");

    RuleAnswer answer = answerer.answer("tai nghe " + tag + " dưới 500k");
    assertThat(answer.products()).isEmpty();
    // Tiền giữ 2 chữ số thập phân như mọi API khác (không thành "5E+5" khi ra JSON).
    assertThat(answer.query().maxPrice().toPlainString()).isEqualTo("500000.00");
    assertThat(answer.answer()).contains("chưa tìm thấy").contains("500.000 ₫");
  }

  @Test
  void readsSortAndAnswersFromTheResults() {
    RuleAnswer answer = answerer.answer("tai nghe " + tag + " rẻ nhất");
    assertThat(answer.query().sort()).isEqualTo("price_asc");
    assertThat(answer.products().get(0).name()).isEqualTo("Tai nghe " + tag + " Basic");
    assertThat(answer.answer()).startsWith("Mình tìm được 3 sản phẩm").contains("1.500.000 ₫");
  }

  @Test
  void usesACategoryNamedInTheQuestion() {
    RuleAnswer answer = answerer.answer("cho mình xem điện thoại " + tag);
    assertThat(names(answer)).containsExactly("Galaxy " + tag);
    assertThat(answer.answer()).contains("thuộc Điện thoại " + tag);
  }

  @Test
  void matchesAPartOfACategoryName() {
    Category watches = categories.save(Category.builder().name("Đồng hồ " + tag + " & Vòng tay").build());
    support.product(support.user(Role.SELLER, SellerStatus.APPROVED), watches, "Watch " + tag, "3000000.00", 1);
    RuleAnswer answer = answerer.answer("tìm đồng hồ " + tag);
    assertThat(names(answer)).containsExactly("Watch " + tag);
    assertThat(answer.answer()).contains("thuộc Đồng hồ " + tag + " & Vòng tay");
  }

  @Test
  void triesWordPairsButNotSingleWords() {
    // "tai nghe <tag> chống ồn" không có trong tên nào, nhưng cặp "nghe <tag>" thì có.
    assertThat(answerer.answer("tai nghe " + tag + " chống ồn").products()).isNotEmpty();
    // Không thử riêng "nghe": đứng một mình nó khớp lung tung (cả mô tả của loa).
    assertThat(answerer.answer("nghe chống ồn").products()).isEmpty();
  }

  @Test
  void answersFaqForeignCurrencyAndUnknownQuestionsWithoutSearching() {
    RuleAnswer payment = answerer.answer("Thanh toán thế nào vậy?");
    assertThat(payment.answer()).contains("COD").contains("VNPAY").contains("24 giờ");
    assertThat(payment.query()).isNull();

    // Có thêm chữ quanh từ khoá chủ đề vẫn là câu hỏi FAQ, không đem đi tìm product.
    RuleAnswer voucher = answerer.answer("voucher dùng sao");
    assertThat(voucher.answer()).contains("mã voucher");
    assertThat(voucher.products()).isEmpty();
    assertThat(answerer.answer("có giao hàng tận nơi không").answer()).contains("Đã nhận hàng");

    assertThat(answerer.answer("xin chào").answer()).startsWith("Xin chào");
    assertThat(answerer.answer("tai nghe dưới 50 usd").answer()).contains("VND");
    assertThat(answerer.answer("zzqq").answer()).contains("chưa tìm thấy");
    assertThat(answerer.answer("???").answer()).startsWith("Mình chưa hiểu");
  }

  @Test
  void theSharedSearchNormalisesUntrustedArgumentsAndTheToolKeepsTheLastResult() {
    ProductSearchTools tools = new ProductSearchTools(productSearch);
    // Giá âm bị bỏ, rating kẹp về 5, sort lạ bỏ, min > max thì đổi chỗ.
    tools.searchProducts("tai nghe " + tag, -1.0, null, 9.0, "random");
    assertThat(tools.lastResult().query().minPrice()).isNull();
    assertThat(tools.lastResult().query().minRating()).isEqualByComparingTo("5");
    assertThat(tools.lastResult().query().sort()).isNull();

    var swapped = productSearch.search("tai nghe " + tag, null, new BigDecimal("5000000"), new BigDecimal("1000000"), null, null);
    assertThat(swapped.query().minPrice()).isEqualByComparingTo("1000000");
    assertThat(swapped.products()).hasSize(2);

    // Tối đa 5 product.
    assertThat(productSearch.search(null, null, null, null, null, null).products()).hasSizeLessThanOrEqualTo(5);
  }

  private static java.util.List<String> names(RuleAnswer answer) {
    return answer.products().stream().map(AiProductResponse::name).toList();
  }
}
