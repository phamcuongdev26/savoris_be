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
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", 400),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request", 400),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method not allowed", 405),

    // Resource not found
    NOT_FOUND("NOT_FOUND", "Resource not found", 404),
    STAFF_NOT_FOUND("STAFF_NOT_FOUND", "Staff not found", 404),
    BRANCH_NOT_FOUND("BRANCH_NOT_FOUND", "Branch not found", 404),

    // Conflict errors
    CONFLICT("CONFLICT", "Resource already exists", 409),
    DUPLICATE_USERNAME("DUPLICATE_USERNAME", "Username already exists", 409),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists", 409),
    DUPLICATE_CODE("DUPLICATE_CODE", "Code already exists", 409),
    DUPLICATE_TABLE_NAME("DUPLICATE_TABLE_NAME", "Another table of this branch already has this name", 409),

    // Invalid state
    INVALID_STATE("INVALID_STATE", "Operation not allowed in current state", 409),
    TABLE_OCCUPIED("TABLE_OCCUPIED", "Table is occupied", 409),
    ORDER_NOT_PAID("ORDER_NOT_PAID", "Order has not been paid yet", 409),
    ALREADY_PAID("ALREADY_PAID", "Order has already been paid", 409),
    INSUFFICIENT_CASH("INSUFFICIENT_CASH", "Cash received is less than the order total", 400),
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_METHOD_NOT_SUPPORTED", "Bank transfer is only for web takeaway orders the guest chose to pay by transfer", 400),
    MENU_ITEM_UNAVAILABLE("MENU_ITEM_UNAVAILABLE", "Menu item is not available at this branch", 409),
    PROMOTION_NOT_APPLICABLE("PROMOTION_NOT_APPLICABLE", "Promotion code cannot be used for this order", 409),
    NOT_ENOUGH_POINTS("NOT_ENOUGH_POINTS", "Customer does not have enough points", 409),

    // Peak hours
    BRANCH_BUSY("BRANCH_BUSY", "Kitchen is at full capacity, takeaway and delivery orders are paused", 503),
    IMAGE_STORAGE_NOT_CONFIGURED("IMAGE_STORAGE_NOT_CONFIGURED", "Image upload is not set up: set CLOUDINARY_URL on the server", 503),
    IMAGE_UPLOAD_FAILED("IMAGE_UPLOAD_FAILED", "Could not store the image, try again", 502),

    // Server errors
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
