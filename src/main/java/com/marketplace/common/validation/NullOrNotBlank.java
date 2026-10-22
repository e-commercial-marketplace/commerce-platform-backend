package com.marketplace.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Trường tuỳ chọn của request cập nhật: không gửi ({@code null}) thì hợp lệ, còn đã gửi thì phải có ít
 * nhất một ký tự không phải khoảng trắng. Dùng cho các PATCH như đổi tên product, tên user.
 *
 * <p>Đây là annotation ghép từ {@code @Pattern} (Bean Validation tự hiểu, không cần viết validator):
 * {@code @Pattern} bỏ qua {@code null}, và {@code (?s)} cho dấu chấm khớp cả xuống dòng.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Pattern(regexp = "(?s).*\\S.*")
@ReportAsSingleViolation // báo một lỗi với message bên dưới, thay vì message của @Pattern
public @interface NullOrNotBlank {

  String message() default "must not be blank";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
