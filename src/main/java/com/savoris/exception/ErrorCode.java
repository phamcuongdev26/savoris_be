package com.savoris.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // Auth errors
    UNAUTHORIZED("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục", 401),
    FORBIDDEN("FORBIDDEN", "Bạn không có quyền truy cập", 403),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng", 401),
    ACCOUNT_RESIGNED("ACCOUNT_RESIGNED", "Tài khoản đã bị khóa hoặc ngừng hoạt động", 403),
    INVALID_TOKEN("INVALID_TOKEN", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn", 401),
    REFRESH_TOKEN_REVOKED("REFRESH_TOKEN_REVOKED", "Phiên đăng nhập đã bị thu hồi", 401),
    TOO_MANY_LOGIN_ATTEMPTS("TOO_MANY_LOGIN_ATTEMPTS", "Bạn đăng nhập sai quá nhiều lần, vui lòng thử lại sau", 429),

    // Validation errors
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu không hợp lệ", 400),
    INVALID_REQUEST("INVALID_REQUEST", "Yêu cầu không hợp lệ", 400),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ", 405),

    // Resource not found
    NOT_FOUND("NOT_FOUND", "Không tìm thấy tài nguyên", 404),
    STAFF_NOT_FOUND("STAFF_NOT_FOUND", "Không tìm thấy nhân viên", 404),
    BRANCH_NOT_FOUND("BRANCH_NOT_FOUND", "Không tìm thấy chi nhánh", 404),

    // Conflict errors
    CONFLICT("CONFLICT", "Dữ liệu đã tồn tại", 409),
    DUPLICATE_USERNAME("DUPLICATE_USERNAME", "Tên đăng nhập đã được sử dụng", 409),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email đã được sử dụng", 409),
    DUPLICATE_CODE("DUPLICATE_CODE", "Mã đã tồn tại", 409),
    DUPLICATE_TABLE_NAME("DUPLICATE_TABLE_NAME", "Tên bàn đã tồn tại trong chi nhánh này", 409),

    // Invalid state
    INVALID_STATE("INVALID_STATE", "Thao tác không hợp lệ ở trạng thái hiện tại", 409),
    TABLE_OCCUPIED("TABLE_OCCUPIED", "Bàn đang có khách", 409),
    ORDER_NOT_PAID("ORDER_NOT_PAID", "Đơn hàng chưa được thanh toán", 409),
    ALREADY_PAID("ALREADY_PAID", "Đơn hàng đã được thanh toán", 409),
    INSUFFICIENT_CASH("INSUFFICIENT_CASH", "Tiền khách đưa ít hơn tổng đơn hàng", 400),
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_METHOD_NOT_SUPPORTED", "Chuyển khoản chỉ áp dụng cho đơn mang về từ website mà khách chọn thanh toán chuyển khoản", 400),
    MENU_ITEM_UNAVAILABLE("MENU_ITEM_UNAVAILABLE", "Món ăn không có sẵn tại chi nhánh này", 409),
    // Peak hours
    BRANCH_BUSY("BRANCH_BUSY", "Bếp đang quá tải, tạm dừng nhận đơn mang về", 503),
    IMAGE_STORAGE_NOT_CONFIGURED("IMAGE_STORAGE_NOT_CONFIGURED", "Chưa cấu hình lưu trữ ảnh: cần đặt CLOUDINARY_URL trên máy chủ", 503),
    IMAGE_UPLOAD_FAILED("IMAGE_UPLOAD_FAILED", "Không thể lưu ảnh, vui lòng thử lại", 502),

    // Server errors
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi máy chủ nội bộ", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
