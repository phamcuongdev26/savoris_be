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

## B2 — ĐÃ HOÀN THÀNH

### B2.1 — Opening hours (commit a6d23c1, fix codex faf934a)

**Root cause của 500:** `persist()` dùng `p.get("weekday")`, `pick(..., "opensAt", ...)` truyền thẳng `String` vào cột `integer` / `time`. PostgreSQL JDBC driver không tự convert String → time.

**Fix:**
- `extractWeekday()` parse + validate weekday 1–7
- `parseTime()` parse String→`LocalTime` (HH:mm / HH:mm:ss); ném 400 nếu sai định dạng
- Validate `opensAt < closesAt` khi `closed=false`
- Truyền `LocalTime` object vào JdbcTemplate → driver convert đúng sang `time without time zone`
- Thêm `PUT /admin/website/hours/week`: nhận `{branchId, days[]}`, upsert tất cả trong một transaction (`@Transactional`); dùng `ON CONFLICT DO UPDATE`

**Codex P2 đã áp dụng:**
- Reject fractional `branchId` (e.g. `1.9` → không truncate thành `1`)
- Reject duplicate weekday trong cùng một `days` list (400)

**GlobalExceptionHandler:**
- `DataIntegrityViolationException` → 409, message Vietnamese
- Thêm `DataAccessException` handler → 500, full log

**Tests (AuthBranchHttpTest.openingHoursValidationAndUpsert):**
- Valid save → 201 ✓
- Duplicate weekday → 409 ✓
- Invalid time (`25:00`) → 400 ✓
- `opensAt >= closesAt` → 400 ✓
- MANAGER own branch → 201 ✓
- MANAGER other branch → 403 ✓
- Week upsert (idempotent) → 200 ✓
- Week cross-branch → 403 ✓

### B2.2 — Dịch ErrorCode + GlobalExceptionHandler (commit 7d84745)

Dịch toàn bộ `message` (không đổi `code`) trong `ErrorCode.java` và inline messages trong `GlobalExceptionHandler`.

Codex: "No regression or actionable defect."

### B2.3 — Sửa permission matrix (commit 81d1b6e)

**Thay đổi:**

| Thay đổi | Trước | Sau |
|---|---|---|
| `Roles.KITCHEN` | `ADMIN, CHEF` | `ADMIN, MANAGER, CHEF` |
| `BranchMenuController.setAvailability` | `Roles.SERVICE` (tất cả 4 role) | `Roles.KITCHEN` (ADMIN, MANAGER, CHEF) |

**Permission matrix thực tế sau B2.3 (đọc từ code):**

| Capability | ADMIN | MANAGER | STAFF | CHEF |
|---|---|---|---|---|
| Kitchen ops (start/ready/void-resolve) | ✓ | ✓ | – | ✓ |
| Mark dish sold out (setAvailability) | ✓ | ✓ | – | ✓ |
| Edit dishes/prices/categories | ✓ | – | – | – |
| Tables & areas (write) | ✓ | ✓ | – | – |
| POS, serving, payment | ✓ | ✓ | ✓ | – |
| Reservations: create, confirm, cancel | ✓ | ✓ | ✓ | – |
| Branch details / hours (write) | ✓ | ✓ | – | – |
| Staff accounts (write) | ✓ | ✓ (STAFF/CHEF own branch) | – | – |
| Chain-wide reports | ✓ | – | – | – |
| Branch reports | ✓ | ✓ | – | – |
| Website homepage (write) | ✓ | – | – | – |
| Website branch page / gallery | ✓ | ✓ | – | – |

**Tests (SecurityRulesTest.permissionMatrixForWriteEndpoints):**
- STAFF blocked from kitchen start → 403 ✓
- MANAGER/CHEF allowed for kitchen start → 404 (item not exist, not 403) ✓
- STAFF blocked from setAvailability → 403 ✓
- CHEF/MANAGER allowed for setAvailability → 200 ✓
- Cross-branch MANAGER → 403 ✓

Codex: "The changes consistently expand the kitchen permission to include MANAGER. No introduced bug."

### Kết quả test sau B2
`mvn test`: **80 pass, 1 skip (PeakLoadTest, pre-existing)**

| Test class | Trước B2 | Sau B2 | Ghi chú |
|---|---|---|---|
| AuthBranchHttpTest | 10 | 11 | +openingHoursValidationAndUpsert |
| SecurityRulesTest | 13 | 14 | +permissionMatrixForWriteEndpoints |
| Tất cả còn lại | — | — | Không thay đổi |
| **TỔNG** | **78** | **80** | +2 test mới |

### Commits B2
```
faf934a  Fix codex P2 findings on week upsert endpoint
81d1b6e  B2.3: Fix permission matrix — MANAGER in KITCHEN, STAFF out of availability
a6d23c1  B2.1: Fix opening_hour persist (500→correct errors) and add week upsert
7d84745  B2.2: Translate all client-facing ErrorCode messages to Vietnamese
```

### Permission matrix check — thực tế vs. yêu cầu (commit b10b41e)

Đọc từ code (Roles.java + tất cả @PreAuthorize + BranchAccessGuard):

| Capability | ADMIN | MANAGER | STAFF | CHEF | Ghi chú |
|---|---|---|---|---|---|
| Kitchen ops (start/ready/void-resolve) | ✓ | ✓ | – | ✓ | Roles.KITCHEN ✓ |
| Mark dish sold out | ✓ | ✓ | – | ✓ | Roles.KITCHEN ✓ |
| Edit dishes/prices/categories | ✓ | – | – | – | Roles.ADMIN ✓ |
| Tables & areas (write) | ✓ | ✓ | – | – | Roles.MANAGEMENT ✓ |
| POS, serving, payment | ✓ | ✓ | ✓ | – | Roles.ORDER_WRITE/PAYMENT ✓ |
| Reservations: create, confirm, cancel | ✓ | ✓ | ✓ | – | Roles.FRONT_OF_HOUSE ✓ |
| Branch details / hours (write) | ✓ | ✓ | – | – | Roles.MANAGEMENT ✓ |
| Pause/resume takeaway | – | – | – | – | Pending B3 ✓ |
| Staff accounts (write) | ✓ | ✓ | – | – | MANAGEMENT + service rules ✓ |
| Chain-wide reports | ✓ | – | – | – | Roles.ADMIN ✓ |
| Branch reports | ✓ | ✓ | – | – | Roles.MANAGEMENT ✓ |
| Website homepage (write) | ✓ | – | – | – | Roles.ADMIN ✓ |
| Website branch page + image upload | ✓ | **FIX** | – | – | **MISMATCH → fixed** |

**Mismatch tìm thấy (1 loại, 2 endpoint):**
- `POST /images/site` (ImageController) → ADMIN-only → đổi sang `Roles.MANAGEMENT`
- `POST /admin/media` (MediaController) → ADMIN-only → đổi sang `Roles.MANAGEMENT`

`DELETE /admin/media/{name}` giữ nguyên ADMIN để tránh MANAGER xóa ảnh của chi nhánh khác.

**Commit fix:** `b10b41e — B2 fix: allow MANAGER to upload site/gallery images`  
**Codex b10b41e:** "Không phát hiện lỗi chức năng hoặc bảo mật mới."

### Kết quả test sau permission fix
`mvn test`: **81 pass, 1 skip**

| Test class | Sau fix | Ghi chú |
|---|---|---|
| ImageUploadTest | 3 | +managerCanUploadSiteAndLocalImages_butNotStaffOrChef |
| Tất cả còn lại | — | Không thay đổi |

### Shell status
- Dev server (PID 49658): đang chạy trên local — giữ lại
- Codex processes: chạy trong các terminal riêng — không ảnh hưởng

---

## B3 — CHƯA BẮT ĐẦU

- Branch-level config columns cho peak hour (reservation_slot_guests, takeaway_paused, …)
- Weighted load calculation
- SSE endpoint cho kitchen

## B4 — CHƯA BẮT ĐẦU

- Demo data migration / seed update phù hợp phạm vi mới
