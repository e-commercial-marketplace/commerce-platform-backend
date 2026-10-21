package com.marketplace.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.marketplace.common.util.VietnamesePhone;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Số di động Việt Nam theo {@link VietnamesePhone#PATTERN}; chuỗi rỗng cũng hợp lệ (nghĩa là xoá số),
 * {@code null} nghĩa là không đổi. Dùng chung cho hồ sơ người dùng và các API admin.
 */
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Pattern(regexp = VietnamesePhone.PATTERN)
@ReportAsSingleViolation
public @interface VietnamesePhoneNumber {

  String message() default "must be a Vietnamese mobile number";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
