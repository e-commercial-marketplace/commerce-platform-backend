package com.marketplace.common.response;

import java.util.List;

/**
 * Khung response cho API phân trang như product list, order list hoặc shipment list.
 *
 * <p>{@code page} bắt đầu từ 0 theo Spring Data, vì vậy request trang đầu tiên dùng {@code page=0}
 * chứ không phải {@code page=1}. {@code content} chỉ chứa item của trang hiện tại; client dùng
 * {@code totalElements} hoặc {@code totalPages} để quyết định có tải tiếp hay không.
 *
 * <p>Ví dụ payload: {@code new PageResponse<>(products, 0, 20, 45, 3)} nghĩa là trang đầu có 20
 * item trong tổng 45 item và cần 3 trang. Service nên trả list rỗng thay vì {@code null} khi trang
 * hợp lệ không có item.
 *
 * @param content danh sách item của trang hiện tại, nên là list không-null
 * @param page số trang hiện tại, bắt đầu từ 0
 * @param size số item yêu cầu mỗi trang
 * @param totalElements tổng số item trên mọi trang
 * @param totalPages tổng số trang
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
