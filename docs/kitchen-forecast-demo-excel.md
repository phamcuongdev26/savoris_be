# File Excel mẫu cho dự báo bếp

Các workbook được lưu trong `src/main/resources/demo-data/kitchen-forecast/` và có thể tải từ màn hình dự báo.

Mỗi file có hai sheet:

- `Scenario`: hai cột `field`, `value`; các field bắt buộc là `kitchenCapacity`, `pendingPortions`, `cookingPortions`, `currentLoadPercent`, `activeChefAccounts`.
- `History`: các cột `hour`, `averageOrders`, `averagePortions`, đủ 24 dòng cho giờ 0–23. Mỗi giờ chỉ được xuất hiện một lần.

Chọn cơ sở trên màn hình trước khi upload. File chỉ tạo response demo trong lần gọi đó; backend không ghi số liệu trong file vào database. Dữ liệu tải giả lập trong workbook là giá trị theo giờ, không phải đơn hàng thật.

Workbook mẫu:

- `tai-thap.xlsx`: tải dự báo khoảng 27% và 40%.
- `cao-diem.xlsx`: tải dự báo khoảng 85% và 123%.
- `qua-tai.xlsx`: tải dự báo khoảng 183% và 227%.

Hai giá trị có thể đổi thứ tự vì file tạo tải xen kẽ theo giờ chẵn/lẻ, còn dự báo lấy đúng hai giờ kế tiếp tại thời điểm upload.
