package com.marketplace.ai.rules;

import com.marketplace.ai.tools.AiProductSearch;
import com.marketplace.dto.response.AiProductQueryResponse;
import com.marketplace.dto.response.AiProductResponse;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.service.CategoryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Trả lời không cần LLM (features/ai/API_CONTRACT.md, "Answering without the LLM"). Dùng khi vượt hạn
 * mức mỗi phút, chưa cấu hình provider hoặc provider lỗi.
 *
 * <p>Thứ tự: (1) ngoại tệ thì nhắc chỉ dùng VND; (2) tách bộ lọc (giá, sao, cách sắp, danh mục) và từ khoá
 * khỏi câu; (3) câu chỉ hỏi về một chủ đề quen thuộc (thanh toán, giao hàng...) thì trả câu có sẵn;
 * (4) còn từ khoá hoặc bộ lọc thì tìm product và trả lời theo mẫu; (5) không có gì thì gợi ý cách hỏi.
 *
 * <p>shortcut: chỉ hiểu tiếng Việt có dấu theo các mẫu câu bên dưới; câu không dấu ("dien thoai"), sai
 * chính tả hay cách nói lạ sẽ rơi vào câu gợi ý. Nâng cấp khi làm RAG hoặc tìm kiếm theo ngữ nghĩa.
 */
@Component
@RequiredArgsConstructor
public class RuleBasedAnswerer {

  /** Câu trả lời và lần tìm đã chạy ({@code query = null} khi không tìm). */
  public record RuleAnswer(String answer, AiProductQueryResponse query, List<AiProductResponse> products) {}

  // ---------- mẫu câu ----------

  // UNICODE_CHARACTER_CLASS: để \b và \p{L} hiểu chữ có dấu tiếng Việt là chữ cái.
  private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE;

  /**
   * Một số tiền: phần số rồi đơn vị tuỳ chọn, ví dụ "5 triệu", "5tr", "500k", "2.500.000". Đơn vị phải
   * đứng riêng, để "500 kg" không thành 500k.
   */
  private static final String AMOUNT =
      "(\\d+(?:[.,]\\d+)*)\\s*(?:(triệu|tr|k|nghìn|ngàn)(?![\\p{L}]))?";

  private static final Pattern RANGE =
      Pattern.compile("(?:từ\\s*)?" + AMOUNT + "\\s*(?:đến|tới|-|~)\\s*" + AMOUNT, FLAGS);
  private static final Pattern MAX =
      Pattern.compile("(?:dưới|không quá|tối đa|nhỏ hơn|<=?)\\s*" + AMOUNT, FLAGS);
  private static final Pattern MIN =
      Pattern.compile("(?:trên|từ|ít nhất|tối thiểu|lớn hơn|>=?)\\s*" + AMOUNT, FLAGS);
  private static final Pattern RATING =
      Pattern.compile("(?:từ\\s*)?(\\d(?:[.,]\\d)?)\\s*sao(?:\\s*trở lên)?", FLAGS);
  private static final Pattern FOREIGN_CURRENCY =
      Pattern.compile("\\$|\\busd\\b|\\beur\\b|đô la|\\bdollar", FLAGS);

  /** Cụm chỉ cách sắp; cụm dài đứng trước để "giá rẻ nhất" không bị khớp thành "giá rẻ". */
  private static final List<Map.Entry<String, String>> SORT_PHRASES =
      List.of(
          Map.entry("rẻ nhất", "price_asc"),
          Map.entry("giá rẻ", "price_asc"),
          Map.entry("giá thấp", "price_asc"),
          Map.entry("đắt nhất", "price_desc"),
          Map.entry("giá cao", "price_desc"),
          Map.entry("cao cấp", "price_desc"),
          Map.entry("đánh giá cao", "rating_desc"),
          Map.entry("đánh giá tốt", "rating_desc"),
          Map.entry("tốt nhất", "rating_desc"),
          Map.entry("nổi bật", "rating_desc"));

  /** Âm tiết không mang nghĩa sản phẩm (lời hỏi, đại từ, đơn vị tiền), bỏ khỏi từ khoá. */
  private static final Set<String> STOP_WORDS =
      Set.of(
          "tìm", "kiếm", "cho", "tôi", "mình", "em", "anh", "chị", "bạn", "muốn", "cần", "mua", "xem",
          "có", "nào", "gì", "giá", "sản", "phẩm", "loại", "cái", "chiếc", "với", "và", "ạ", "nhé", "nha",
          "shop", "khoảng", "tầm", "những", "các", "một", "hãy", "gợi", "ý", "giúp", "thế", "như", "sao",
          "làm", "được", "bao", "nhiêu", "đi", "đ", "đồng", "vnd", "vnđ", "thì", "là", "ở", "đâu", "nhỉ", "vậy");

  // ---------- câu trả lời có sẵn ----------

  private record Faq(List<String> keywords, String answer) {}

  /** Chỉ nhắc lại luật đã có trong docs; không hứa điều backend chưa làm. */
  private static final List<Faq> FAQS =
      List.of(
          new Faq(
              List.of("thanh toán", "trả tiền", "cod", "vnpay"),
              "Có 2 cách thanh toán: COD (trả tiền khi nhận hàng) và VNPAY (trả trước online). Đơn chọn VNPAY "
                  + "phải thanh toán trong 24 giờ kể từ lúc đặt, quá hạn đơn sẽ tự huỷ."),
          new Faq(
              List.of("giao hàng", "vận chuyển", "ship", "nhận hàng", "đã nhận"),
              "Mỗi shop tự giao phần hàng của mình, tự giao hoặc qua đơn vị vận chuyển của nền tảng. Khi nhận đủ "
                  + "hàng, bạn bấm \"Đã nhận hàng\"; nếu không bấm, đơn tự hoàn tất sau 3 ngày kể từ lúc giao."),
          new Faq(
              List.of("huỷ", "hủy"),
              "Shop có thể huỷ từng sản phẩm trong đơn khi chưa giao. Đơn đã thanh toán VNPAY thì không huỷ được "
                  + "vì chưa có quy trình hoàn tiền; đơn VNPAY chưa thanh toán sẽ tự huỷ sau 24 giờ."),
          new Faq(
              List.of("voucher", "mã giảm giá", "khuyến mãi"),
              "Bạn nhập mã voucher ở bước đặt hàng. Voucher giảm theo phần trăm trên tổng tiền sản phẩm, mỗi mã có "
                  + "thời hạn và số lượt dùng; lượt đã dùng không được trả lại khi đơn bị huỷ."),
          new Faq(
              List.of("viết đánh giá", "cách đánh giá", "review"),
              "Bạn đánh giá được một sản phẩm sau khi đơn chứa sản phẩm đó đã hoàn tất, mỗi sản phẩm một lần, "
                  + "từ 1 đến 5 sao kèm nhận xét."),
          new Faq(
              List.of("đặt hàng", "cách mua", "mua hàng"),
              "Bạn thêm sản phẩm vào giỏ, chọn các sản phẩm muốn mua, nhập địa chỉ giao hàng, chọn COD hoặc VNPAY "
                  + "rồi đặt hàng. Một đơn có thể gồm sản phẩm của nhiều shop."),
          new Faq(
              List.of("xin chào", "chào", "hello", "hi", "alo"),
              "Xin chào! Mình là trợ lý của marketplace. Bạn có thể nhờ mình tìm sản phẩm, ví dụ \"tìm tai nghe "
                  + "dưới 2 triệu\", hoặc hỏi về thanh toán, giao hàng, voucher."));

  private static final String FOREIGN_CURRENCY_ANSWER =
      "Hiện marketplace chỉ dùng tiền Việt Nam (VND). Bạn nhập khoảng giá bằng VND giúp mình nhé, ví dụ "
          + "\"dưới 2 triệu\".";
  private static final String HINT =
      "Mình chưa hiểu câu hỏi. Bạn có thể hỏi như: \"tìm tai nghe dưới 2 triệu\", \"điện thoại từ 4 sao\" "
          + "hoặc \"thanh toán thế nào\".";

  /** Số lần tìm tối đa cho một câu (cả cụm rồi từng từ), để một câu hỏi không tạo quá nhiều query. */
  private static final int MAX_ATTEMPTS = 6;

  private final AiProductSearch productSearch;
  private final CategoryService categoryService;

  public RuleAnswer answer(String message) {
    String rest = message.toLowerCase(Locale.ROOT).trim();

    // Bước 1: không tự đổi ngoại tệ (contract), chỉ nhắc dùng VND.
    if (FOREIGN_CURRENCY.matcher(rest).find()) {
      return new RuleAnswer(FOREIGN_CURRENCY_ANSWER, null, List.of());
    }

    // Bước 2: tách bộ lọc; mỗi phần đã hiểu bị xoá khỏi câu, phần còn lại là từ khoá.
    Filters f = new Filters();
    rest = parseRating(rest, f);
    rest = parsePrices(rest, f);
    rest = parseSort(rest, f);
    rest = parseCategory(rest, f);
    Faq faq = FAQS.stream().filter(q -> q.keywords().stream().anyMatch(k -> contains(message, k))).findFirst().orElse(null);
    List<String> words = keywords(rest);

    // Bước 3: có từ khoá chủ đề quen thuộc mà không có bộ lọc sản phẩm → câu có sẵn. Các chữ còn lại
    // ("voucher dùng sao", "có giao hàng tận nơi không") thường chỉ là cách hỏi, không phải tên sản phẩm.
    if (faq != null && !f.any()) {
      return new RuleAnswer(faq.answer(), null, List.of());
    }

    // Bước 4: còn từ khoá hoặc bộ lọc thì tìm product.
    if (!words.isEmpty() || f.any()) {
      AiProductSearch.Result first = null;
      for (String text : candidates(words)) {
        AiProductSearch.Result result =
            productSearch.search(text, f.categoryId, f.minPrice, f.maxPrice, f.minRating, f.sort);
        if (first == null) {
          first = result;
        }
        if (!result.products().isEmpty()) {
          return new RuleAnswer(found(result.products(), f), result.query(), result.products());
        }
      }
      return new RuleAnswer(notFound(f, String.join(" ", words)), first.query(), List.of());
    }

    // Bước 5: không hiểu gì.
    return new RuleAnswer(HINT, null, List.of());
  }

  // ---------- tách bộ lọc ----------

  /** Bộ lọc đọc được từ câu hỏi. */
  private static final class Filters {
    BigDecimal minPrice;
    BigDecimal maxPrice;
    BigDecimal minRating;
    String sort;
    UUID categoryId;
    String categoryName;

    boolean any() {
      return minPrice != null || maxPrice != null || minRating != null || sort != null || categoryId != null;
    }
  }

  private static String parseRating(String rest, Filters f) {
    Matcher m = RATING.matcher(rest);
    if (m.find()) {
      f.minRating = new BigDecimal(m.group(1).replace(',', '.'));
      return cut(rest, m);
    }
    return rest;
  }

  /** "từ X đến Y" trước, rồi "dưới X", rồi "trên/từ X"; mỗi kiểu lấy lần xuất hiện đầu tiên. */
  private static String parsePrices(String rest, Filters f) {
    Matcher range = RANGE.matcher(rest);
    if (range.find()) {
      BigDecimal low = amount(range.group(1), range.group(2));
      BigDecimal high = amount(range.group(3), range.group(4));
      if (low != null && high != null) {
        f.minPrice = low;
        f.maxPrice = high;
        return cut(rest, range);
      }
    }
    Matcher max = MAX.matcher(rest);
    if (max.find() && amount(max.group(1), max.group(2)) != null) {
      f.maxPrice = amount(max.group(1), max.group(2));
      rest = cut(rest, max);
    }
    Matcher min = MIN.matcher(rest);
    if (min.find() && amount(min.group(1), min.group(2)) != null) {
      f.minPrice = amount(min.group(1), min.group(2));
      rest = cut(rest, min);
    }
    return rest;
  }

  /**
   * Đổi "5" + "triệu" thành 5000000. Có đơn vị thì "1,5" hoặc "1.5" là số thập phân; không có đơn vị thì
   * dấu chấm/phẩy là dấu ngăn hàng nghìn ("2.500.000"). Số không đơn vị dưới 1000 không phải giá (ví dụ
   * "iphone 15"), trả {@code null}.
   */
  static BigDecimal amount(String number, String unit) {
    if (number == null) {
      return null;
    }
    if (unit == null) {
      BigDecimal plain = new BigDecimal(number.replace(".", "").replace(",", ""));
      return plain.compareTo(BigDecimal.valueOf(1000)) < 0 ? null : plain.setScale(2, RoundingMode.HALF_UP);
    }
    String decimal =
        number.matches("\\d+[.,]\\d{1,2}") ? number.replace(',', '.') : number.replace(".", "").replace(",", "");
    long multiplier = unit.startsWith("t") ? 1_000_000 : 1_000; // triệu/tr hoặc k/nghìn/ngàn
    // 2 chữ số thập phân như mọi số tiền khác của API ("5000000.00", không phải "5E+6").
    return new BigDecimal(decimal).multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_UP);
  }

  private static String parseSort(String rest, Filters f) {
    for (Map.Entry<String, String> phrase : SORT_PHRASES) {
      if (contains(rest, phrase.getKey())) {
        f.sort = phrase.getValue();
        return removeAll(rest, List.of(phrase.getKey()));
      }
    }
    return rest;
  }

  /**
   * Tên danh mục, hoặc một phần của tên tách theo "&" hay "," ("tai nghe" của "Tai nghe & Loa"), nằm
   * trong câu thì lọc theo danh mục đó. Thử cụm dài trước để tên đầy đủ thắng một phần ngắn.
   */
  private String parseCategory(String rest, Filters f) {
    record Alias(String text, CategorySummaryResponse category) {}
    List<Alias> aliases = new ArrayList<>();
    for (CategorySummaryResponse category : categoryService.listAll()) {
      String name = category.name().toLowerCase(Locale.ROOT);
      aliases.add(new Alias(name, category));
      for (String part : name.split("[&,]")) {
        if (part.trim().length() >= 3 && !part.trim().equals(name)) {
          aliases.add(new Alias(part.trim(), category));
        }
      }
    }
    aliases.sort(Comparator.comparingInt((Alias a) -> a.text().length()).reversed());
    for (Alias alias : aliases) {
      if (contains(rest, alias.text())) {
        f.categoryId = alias.category().id();
        f.categoryName = alias.category().name();
        return removeAll(rest, List.of(alias.text()));
      }
    }
    return rest;
  }

  // ---------- từ khoá ----------

  /**
   * Các âm tiết còn lại sau khi bỏ dấu câu và từ thừa. "không" ở cuối là chữ hỏi ("có ... không"), bỏ đi;
   * ở giữa câu thì giữ, vì có thể là tên sản phẩm ("không dây").
   */
  private static List<String> keywords(String rest) {
    List<String> words =
        new ArrayList<>(
            Arrays.stream(rest.split("[\\s?.,!;:()\"'“”]+"))
                .filter(w -> !w.isBlank() && !STOP_WORDS.contains(w))
                .toList());
    if (!words.isEmpty() && words.get(words.size() - 1).equals("không")) {
      words.remove(words.size() - 1);
    }
    return words;
  }

  /**
   * Search khớp nguyên cụm, nên thử cả cụm trước rồi các cặp từ liền nhau ("iphone 15 chính hãng" ra
   * "iphone 15"). Không thử từng từ riêng: một âm tiết tiếng Việt đứng một mình không đủ nghĩa ("nghe"
   * khớp cả mô tả của loa), và không phân biệt được với tên hãng vì nhiều âm tiết cũng không dấu. Câu chỉ có
   * một từ thì cụm chính là từ đó. Không có từ khoá thì chỉ tìm theo bộ lọc.
   */
  private static List<String> candidates(List<String> words) {
    if (words.isEmpty()) {
      return Collections.singletonList(null);
    }
    Set<String> out = new LinkedHashSet<>();
    out.add(String.join(" ", words));
    for (int i = 0; i + 1 < words.size(); i++) {
      out.add(words.get(i) + " " + words.get(i + 1));
    }
    return out.stream().limit(MAX_ATTEMPTS).toList();
  }

  // ---------- câu trả lời theo mẫu ----------

  private static String found(List<AiProductResponse> products, Filters f) {
    String list =
        products.stream()
            .map(p -> "- " + p.name() + ": " + vnd(p.price()) + rating(p))
            .collect(Collectors.joining("\n"));
    return "Mình tìm được " + products.size() + " sản phẩm" + describe(f) + ":\n" + list;
  }

  private static String notFound(Filters f, String text) {
    String what = text.isBlank() ? "" : " cho \"" + text + "\"";
    return "Mình chưa tìm thấy sản phẩm nào" + what + describe(f)
        + ". Bạn thử từ khoá khác hoặc nới khoảng giá nhé.";
  }

  /** Mô tả bộ lọc bằng lời, ví dụ " thuộc Điện thoại, giá dưới 5.000.000 ₫, từ 4 sao". */
  private static String describe(Filters f) {
    List<String> parts = new ArrayList<>();
    if (f.categoryName != null) {
      parts.add("thuộc " + f.categoryName);
    }
    if (f.minPrice != null && f.maxPrice != null) {
      parts.add("giá từ " + vnd(f.minPrice) + " đến " + vnd(f.maxPrice));
    } else if (f.maxPrice != null) {
      parts.add("giá dưới " + vnd(f.maxPrice));
    } else if (f.minPrice != null) {
      parts.add("giá từ " + vnd(f.minPrice));
    }
    if (f.minRating != null) {
      parts.add("từ " + f.minRating.stripTrailingZeros().toPlainString() + " sao");
    }
    return parts.isEmpty() ? "" : " " + String.join(", ", parts);
  }

  private static String rating(AiProductResponse p) {
    return p.reviewCount() == 0 ? "" : " (" + p.ratingAverage().stripTrailingZeros().toPlainString() + "★)";
  }

  /** 5000000 → "5.000.000 ₫". */
  static String vnd(BigDecimal amount) {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    symbols.setGroupingSeparator('.');
    return new DecimalFormat("#,##0", symbols).format(amount) + " ₫";
  }

  // ---------- xử lý chuỗi ----------

  /** Có cụm {@code phrase} đứng riêng (không dính vào chữ khác) trong {@code text} không. */
  private static boolean contains(String text, String phrase) {
    return wordPattern(phrase).matcher(text.toLowerCase(Locale.ROOT)).find();
  }

  private static String removeAll(String text, List<String> phrases) {
    String out = text;
    for (String phrase : phrases) {
      out = wordPattern(phrase).matcher(out).replaceAll(" ");
    }
    return out;
  }

  private static Pattern wordPattern(String phrase) {
    return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])", FLAGS);
  }

  private static String cut(String text, Matcher m) {
    return text.substring(0, m.start()) + " " + text.substring(m.end());
  }
}
