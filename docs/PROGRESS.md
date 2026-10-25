# Tiến độ thu gọn phạm vi (B1–B4)

Branch làm việc: `feature/thu-gon-pham-vi`  
Nhánh `master` KHÔNG được đụng đến cho đến khi frontend sẵn sàng và DB đã backup.

---

## B1 — ĐÃ HOÀN THÀNH

### B1.1 — Xác định phạm vi
Đã xác nhận giữ lại: gọi món, bếp, thanh toán, đặt bàn, mang về, giờ vàng, phân quyền, quản lý nhân viên/chi nhánh, website CMS (gallery + giờ mở cửa).

### B1.2 — Xóa code
Đã xóa 78 file (constant, controller, service, entity, repository, DTO, test) thuộc các module:
- Inventory / Purchasing / Recipes
- Shifts / Timekeeping
- Promotions / Loyalty Points
- Customer entity
- CMS tabs: offer, accolade, testimonial, menu_file

### B1.3 — Migration V36
File: `src/main/resources/db/migration/V36__remove_out_of_scope_modules.sql`

Nội dung:
1. `ALTER TABLE orders DROP COLUMN IF EXISTS discount_amount, points_used, customer_id, promotion_id`
2. `ALTER TABLE reservations DROP COLUMN IF EXISTS customer_id`
3. `DROP TABLE IF EXISTS ... CASCADE` cho 19 bảng của module đã xóa

**CASCADE analysis (đã kiểm tra trên DB local V35→V36):**
Chỉ 2 FK bị cascade-drop, cả hai đều nằm giữa các bảng bị xóa:
- `fk_stock_movements_stock_count_id` (stock_movements → stock_counts)
- `fk_branch_inventories_ingredient_id` (branch_inventories → ingredients)

Không có ràng buộc, index, view nào của bảng GIỮ LẠI bị ảnh hưởng → CASCADE an toàn, không cần đổi.

### Sửa lỗi compile/runtime sau B1
- `GuestOrderResponse`: bỏ `discountAmount` khỏi record signature
- `ReservationResponse`: bỏ `customerId` field
- `BranchMenuServiceImpl`: thêm lại `import java.util.Set`
- `OrderRepository`: bỏ `findByCustomerId` + `findByCustomerIdAndBranchId` (Spring Data lỗi trên field đã xóa)
- `OrderEntity`: bỏ `@Index(columnList = "customer_id")` dead index
- `MediaController`: bỏ sub-query `offer` khỏi DELETE check
- `AuthBranchHttpTest`: bỏ 5 dòng `delete from <dropped-table>` trong setUp
- `OrderFlowTest`: bỏ sub-case gửi `customerId:999999` (field bị ignore, order thành công thay vì 404)

### Commits
```
a5e0575  B1 cleanup: remove dead code found by grep sweep
f453099  B1: Remove inventory, shift, promotion, loyalty, customer, CMS-tabs modules
```
Xác nhận: `git log master..feature/thu-gon-pham-vi` → chỉ 2 commit trên, KHÔNG có trên master.

### Kết quả test sau B1
`mvn test` (TEST_DB_URL=jdbc:postgresql://localhost:5433/savoris?currentSchema=savoris_test): **77 pass, 1 skip**

| File | Trước B1 | Sau B1 | Ghi chú |
|---|---|---|---|
| PricingLoyaltyTest | 9 | 0 | Xóa toàn bộ |
| ReadyMadeAndStockTest | 2 | 0 | Xóa toàn bộ |
| ShiftAndPurchasingTest | 5 | 0 | Xóa toàn bộ |
| TableStockReservationTest | 8 | 0 | Xóa toàn bộ |
| SecurityRulesTest | 14 | 13 | Bỏ staffCannotManageStock... |
| OrderEditingMenuPasswordTest | 6 | 5 | Bỏ test promotion/loyalty |
| OrderFlowTest | 7 | 7 | Bỏ 1 sub-case trong invalidOrdersAreRejected |
| AuthBranchHttpTest | 10 | 10 | Chỉ sửa setUp |
| CashPaymentTest | 6 | 6 | Nguyên vẹn |
| KitchenFlowTest | 4 | 4 | Nguyên vẹn |
| WebTakeawayTest | 8 | 8 | Nguyên vẹn |
| GuestQrOrderTest | 5 | 5 | Nguyên vẹn |
| PeakHourTest | 2 | 2 | Nguyên vẹn |
| PeakLoadTest | 2 | 2 | Nguyên vẹn |
| SecurityRulesTest | — | 13 | Nguyên vẹn (phân quyền) |
| ChainDashboardTest | 2 | 2 | Nguyên vẹn |
| DishPerBranchTest | 1 | 1 | Nguyên vẹn |
| SepayWebhookTest | 6 | 6 | Nguyên vẹn |
| ImageUploadTest | 2 | 2 | Nguyên vẹn |
| SavorisApplicationTests | 1 | 1 | Nguyên vẹn |
| **TỔNG** | **~103** | **77** | 26 test xóa/bỏ |

Các luồng chính KHÔNG mất test: gọi món (OrderFlowTest), bếp (KitchenFlowTest), thanh toán (CashPaymentTest), đặt bàn (AuthBranchHttpTest), mang về (WebTakeawayTest), giờ vàng (PeakHourTest + PeakLoadTest), phân quyền (SecurityRulesTest + AuthBranchHttpTest).

### Codex review (commit f453099) — fix lại tại commit d0f360d
**P1 đã áp dụng (sửa lại):**  
- Commit 3663954 (đã revert về): dùng `isStaff=true` để bỏ toàn bộ validation cho staff — SAI, cho đặt ngoài giờ và vào quá khứ.
- Commit d0f360d (đúng): validate đồng nhất cho cả staff và guest; chỉ sức chứa là có thể vượt với `overrideCapacity=true`, giới hạn ADMIN/MANAGER; STAFF gửi flag → 403; ghi audit log khi vượt.
- Lỗi phụ tìm thấy khi viết test: Jackson 3 dùng all-args constructor Lombok → `boolean` primitive không có trong JSON gây `HttpMessageNotReadableException`; fix: đổi sang `Boolean` wrapper.
- Lỗi setUp: `delete from reservations` chạy sau `staff.deleteAll()` → FK violation; đã di chuyển.

Codex review commit d0f360d: **"Không tìm thấy lỗi nào đáng kể."**

---

## B2 — CHƯA BẮT ĐẦU

Việc cần làm:
- B2.1: Fix `PUT /admin/website/hours` 500; thêm `PUT /admin/website/hours/week` (upsert 7 ngày 1 lần); GlobalExceptionHandler cho `DataIntegrityViolationException` → 409
- B2.2: Dịch toàn bộ message trong `ErrorCode.java` sang tiếng Việt
- B2.3: Sửa permission matrix (nếu có sai lệch sau B1)

## B3 — CHƯA BẮT ĐẦU

- Branch-level config columns cho peak hour (reservation_slot_guests, takeaway_paused, …)
- Weighted load calculation
- SSE endpoint cho kitchen

## B4 — CHƯA BẮT ĐẦU

- Demo data migration / seed update phù hợp phạm vi mới
