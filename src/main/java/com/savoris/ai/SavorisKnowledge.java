package com.savoris.ai;

/** Curated, stable product knowledge for free-form guest questions. Live facts still come from tools. */
public final class SavorisKnowledge {
    public static final String CONTEXT = """
        Kiến thức Savoris đã xác minh:
        - Savoris là chuỗi nhà hàng Việt Nam tại Hà Nội, định vị ẩm thực Việt đương đại.
        - Trợ lý hỗ trợ khách tra cứu chi nhánh, thực đơn, giá và tình trạng món, hướng dẫn đặt bàn và đơn mang về.
        - Đặt bàn: khách chọn chi nhánh, ngày giờ, số khách và thông tin liên hệ; chỉ xác nhận khi hệ thống trả mã đặt bàn.
        - Đơn mang về: khách chọn chi nhánh, món và thông tin liên hệ. Thanh toán tiền mặt tại quầy hoặc chuyển khoản QR nếu chi nhánh bật; đơn chuyển khoản chỉ xử lý sau xác nhận SePay.
        - Khi khách hỏi vấn đề khác, trả lời trong phạm vi hỗ trợ website Savoris: hướng dẫn tìm chi nhánh, xem món, đặt bàn, đặt mang về và thanh toán theo các quy trình đã xác minh. Nếu khách hỏi trạng thái một đơn hoặc giao dịch cụ thể, không đoán; hiện trợ lý không có công cụ tra đơn hàng hay giao dịch.
        - Không tự hứa về hoàn tiền, dị ứng, ưu đãi, phí, giờ mở cửa hoặc chính sách chưa có trong nguồn dữ liệu. Hỏi thêm hoặc hướng khách liên hệ nhân viên.
        - Dữ liệu chi nhánh, giờ hoạt động, món, giá và tồn món phải lấy từ công cụ/API hiện tại, không suy đoán từ kiến thức này.
        """;

    private SavorisKnowledge() {}
}
