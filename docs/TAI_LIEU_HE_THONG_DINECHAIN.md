# Tài liệu Hệ thống DineChain (Savoris)

**Ngày cập nhật:** 26/10/2026  
**Backend:** Spring Boot 4.1.1 · Java 17 · PostgreSQL · Flyway  
**Frontend:** React + TypeScript · Vercel  
**Backend deploy:** Render (auto-deploy từ `master`)

---

## 1. Tổng quan hệ thống

DineChain là nền tảng quản lý chuỗi nhà hàng nhiều chi nhánh. Một tài khoản **ADMIN** quản lý toàn chuỗi; mỗi chi nhánh có **MANAGER** riêng, bên dưới là **STAFF** (phục vụ/thu ngân) và **CHEF** (bếp).

### Môi trường hiện tại

| Thành phần | URL |
|---|---|
| Frontend (Vercel) | https://savoris-fe.vercel.app |
| Backend API (Render) | https://savoris-be.onrender.com/api/v1 |
| Swagger UI | https://savoris-be.onrender.com/swagger-ui.html |
| GitHub FE | https://github.com/xdat310/savoris-fe |
| GitHub BE | https://github.com/phamcuongdev26/savoris_be |

### Tài khoản demo (production)

| Username | Mật khẩu | Vai trò | Chi nhánh |
|---|---|---|---|
| admin | savoris123 | ADMIN | Toàn chuỗi |
| manager.cn01 | savoris123 | MANAGER | CN01 – Hoàn Kiếm |
| manager.cn02 | savoris123 | MANAGER | CN02 – Cầu Giấy |
| manager.cn03 | savoris123 | MANAGER | CN03 – Tây Hồ |
| manager.cn04 | savoris123 | MANAGER | CN04 – Long Biên |
| staff1.cn01 | savoris123 | STAFF | CN01 |
| chef.cn01 | savoris123 | CHEF | CN01 |

---

## 2. Phân quyền các Role

### 2.1 Quy tắc chung

- **ADMIN** – chủ chuỗi: quyền tuyệt đối, thấy tất cả chi nhánh.
- **MANAGER** – quản lý chi nhánh: toàn quyền trong chi nhánh của mình, không được đụng chi nhánh khác.
- **STAFF** – nhân viên phục vụ / thu ngân: tạo order, phục vụ, thanh toán, đặt bàn. Không vào bếp, không sửa menu.
- **CHEF** – đầu bếp: chỉ thao tác màn hình bếp (xem queue, chuyển trạng thái món, đánh dấu hết hàng). Không tạo order, không thu tiền.

Ngoài role, hệ thống còn enforce **branch scope** ở tầng service qua `BranchAccessGuard`: MANAGER/STAFF/CHEF chỉ thao tác được với chi nhánh của mình.

### 2.2 Ma trận phân quyền chi tiết

| Chức năng | ADMIN | MANAGER | STAFF | CHEF |
|---|:---:|:---:|:---:|:---:|
| **QUẢN LÝ CHUỖI** |||||
| Tạo / xóa chi nhánh | ✅ | ❌ | ❌ | ❌ |
| Xem danh sách tất cả chi nhánh | ✅ | ❌ | ❌ | ❌ |
| Dashboard toàn chuỗi (doanh thu, đơn hàng tất cả chi nhánh) | ✅ | ❌ | ❌ | ❌ |
| Dashboard chi nhánh (doanh thu, đơn hàng chi nhánh mình) | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| Audit log (nhật ký hành động) | ✅ | ❌ | ❌ | ❌ |
| **QUẢN LÝ CHI NHÁNH** |||||
| Sửa thông tin chi nhánh | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| Đổi Manager / Kitchen Capacity | ✅ | ❌ | ❌ | ❌ |
| Tạm ngưng / mở lại nhận đơn mang về | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| Xem thông tin chi nhánh | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| **NHÂN VIÊN** |||||
| Tạo tài khoản STAFF / CHEF | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| Tạo tài khoản MANAGER / ADMIN | ✅ | ❌ | ❌ | ❌ |
| Sửa / khóa nhân viên | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| **THỰC ĐƠN** |||||
| Tạo / sửa / xóa category, item (toàn chuỗi) | ✅ | ❌ | ❌ | ❌ |
| Sửa giá / thông tin món theo chi nhánh | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |
| Bật/tắt món theo chi nhánh | ✅ | ✅ | ❌ | ✅ |
| Xem thực đơn (public) | ✅ | ✅ | ✅ | ✅ |
| **BÀN & KHU VỰC** |||||
| Tạo / sửa / xóa bàn & khu vực | ✅ | ✅ | ❌ | ❌ |
| Xem sơ đồ bàn | ✅ | ✅ | ✅ | ❌ |
| **ĐẶT BÀN** |||||
| Tạo đặt bàn (POS) | ✅ | ✅ | ✅ | ❌ |
| Xác nhận / hủy đặt bàn | ✅ | ✅ | ✅ | ❌ |
| Đặt bàn vượt sức chứa (overrideCapacity) | ✅ | ✅ | ❌ | ❌ |
| Khách tự đặt bàn (public) | Không cần đăng nhập | | | |
| **GỌI MÓN & ORDER** |||||
| Tạo order tại bàn (POS) | ✅ | ✅ | ✅ | ❌ |
| Thêm / sửa món trong order đang chờ | ✅ | ✅ | ✅ | ❌ |
| Yêu cầu hủy món | ✅ | ✅ | ✅ | ❌ |
| Hoàn tất order (gọi đủ món) | ✅ | ✅ | ✅ | ❌ |
| Khách tự quét QR gọi món | Không cần đăng nhập | | | |
| **BẾP** |||||
| Xem queue bếp | ✅ | ✅ | ❌ | ✅ |
| Bắt đầu nấu / xong / phục vụ | ✅ | ✅ | ❌ | ✅ |
| Duyệt / từ chối hủy món | ✅ | ✅ | ❌ | ✅ |
| Xem tải bếp (load / wait estimate) | ✅ | ✅ | ❌ | ✅ |
| **THANH TOÁN** |||||
| Tính tiền / in bill | ✅ | ✅ | ✅ | ❌ |
| Thu tiền mặt | ✅ | ✅ | ✅ | ❌ |
| Xác nhận chuyển khoản (cashier fallback) | ✅ | ✅ | ✅ | ❌ |
| Hoàn tiền | ✅ | ✅ | ❌ | ❌ |
| **ĐƠN MANG VỀ (Web Takeaway)** |||||
| Khách đặt mang về online | Không cần đăng nhập | | | |
| Xem danh sách đơn mang về | ✅ | ✅ | ✅ | ❌ |
| Xác nhận thanh toán đơn mang về | ✅ | ✅ | ✅ | ❌ |
| **WEBSITE & CMS** |||||
| Sửa nội dung trang chủ (toàn chuỗi) | ✅ | ❌ | ❌ | ❌ |
| Sửa trang chi nhánh / gallery | ✅ | ❌ | ❌ | ❌ |
| Upload ảnh site / ảnh local | ✅ | ❌ | ❌ | ❌ |
| Xem website (public) | Không cần đăng nhập | | | |
| **GIỜ MỞ CỬA** |||||
| Cài đặt giờ mở cửa | ✅ | ✅ (chi nhánh mình) | ❌ | ❌ |

---

## 3. Các luồng nghiệp vụ hiện tại

### 3.1 Luồng gọi món tại bàn (Dine-in)

```
Khách ngồi vào bàn
    │
    ▼
STAFF mở POS → chọn bàn → tạo Order (AWAITING_PAYMENT)
    │
    ▼
STAFF thêm món → hệ thống tạo OrderItem (PENDING)
    │
    ▼
Thông báo đẩy xuống màn hình bếp
    │
    ▼
CHEF thấy ticket → nhấn "Bắt đầu nấu" → COOKING
    │
    ▼
CHEF nấu xong → nhấn "Xong" → READY
    │
    ▼
STAFF phục vụ → nhấn "Đã mang ra" → SERVED
    │
    ▼
Tất cả món SERVED → STAFF nhấn "Hoàn tất order"
→ Order chuyển sang COMPLETED
    │
    ▼
STAFF chọn phương thức thanh toán
    ├── Tiền mặt → thu tiền → PAID
    └── Chuyển khoản → VietQR → SePay webhook tự xác nhận
```

**Trạng thái Order:** `AWAITING_PAYMENT` → `COMPLETED` → `PAID`  
**Trạng thái OrderItem:** `PENDING` → `COOKING` → `READY` → `SERVED`  
(Nhánh hủy: `VOID_REQUESTED` → CHEF duyệt → `VOIDED` hoặc từ chối)

---

### 3.2 Luồng hủy món (Void)

```
STAFF yêu cầu hủy món → VOID_REQUESTED
    │
    ▼
Màn hình bếp hiện queue void
    │
    ├── CHEF duyệt → VOIDED (món bị xóa khỏi bill)
    └── CHEF từ chối → trả về PENDING/COOKING
```

---

### 3.3 Luồng đặt bàn

```
Khách (public) / STAFF (POS) tạo đặt bàn
    │
    ├── Hệ thống kiểm tra: ngày tương lai + trong giờ mở cửa + còn chỗ
    └── Nếu STAFF với overrideCapacity=true (MANAGER+): bỏ qua kiểm tra chỗ
    │
    ▼
Đặt bàn tạo với trạng thái PENDING
    │
    ▼
STAFF/MANAGER xác nhận → CONFIRMED
    │                      hoặc hủy → CANCELLED
    └── Nếu khách không đến: MANAGER đánh dấu NO_SHOW
```

---

### 3.4 Luồng đơn mang về (Web Takeaway)

```
Khách truy cập https://savoris-fe.vercel.app
    │
    ▼
Chọn chi nhánh → xem menu takeaway
    │
    ▼
Hệ thống kiểm tra:
    ├── Chi nhánh đang mở (theo giờ mở cửa)
    ├── Chi nhánh chưa bị PAUSE (takeaway_paused = false)
    └── Bếp không quá tải (load < 100%)
    │
    ▼
Khách đặt món + chọn giờ lấy (pickupAt) + phương thức TT
    │
    ├── CASH → tạo order, khách đến trả tiền mặt khi lấy
    └── TRANSFER → VietQR sinh mã → SePay webhook tự xác nhận
                   (fallback: STAFF xác nhận thủ công)
    │
    ▼
Order tạo với trạng thái AWAITING_PAYMENT
    │
    ▼
Sau khi thanh toán xác nhận → COMPLETED
    │
    ▼
OrderItems vào queue bếp (PENDING)
    → Bếp nấu → READY → hệ thống thông báo khách đến lấy
```

---

### 3.5 Luồng thanh toán

```
Order ở trạng thái COMPLETED
    │
    ▼
STAFF chọn: POST /payments/{orderId}
    │
    ├── payMethod = CASH
    │       → Ghi nhận payment COMPLETED ngay
    │       → Order → PAID
    │
    └── payMethod = TRANSFER
            → Sinh VietQR (SePay)
            → Chờ webhook SePay callback
            → Nếu webhook confirm → PAID
            └── Fallback: STAFF nhấn "Đã nhận tiền" thủ công
```

---

### 3.6 Tải bếp & Giờ vàng

```
Hệ thống tính tải bếp theo chi nhánh:
    ├── pending_portions + cooking_portions / kitchen_capacity (%) 
    ├── NORMAL (< 70%)  │  BUSY (70–99%)  │  OVERLOADED (≥ 100%)
    │
    └── Ước tính thời gian chờ:
        wait = active_portions × window_minutes / ready_recently
        (cần ≥ 5 món đã xong trong 30 phút để ước tính)

OVERLOADED → acceptingTakeaway = false → chặn đơn online mới
Takeaway paused → acceptingTakeaway = false (bất kể tải)
```

---

## 4. Trạng thái hoàn thành

### ✅ Đã hoàn thành

| Hạng mục | Mô tả |
|---|---|
| **B1 – Thu gọn phạm vi** | Xóa 78 file module không dùng: Inventory, Shift, Promotion, Loyalty, Customer |
| **B1 – Migration V36** | Drop 19 bảng không cần thiết, clean FK cascade |
| **B2 – Opening hours** | CRUD giờ mở cửa, validate định dạng + khoảng thời gian, upsert cả tuần |
| **B2 – Dịch lỗi** | Toàn bộ ErrorCode + GlobalExceptionHandler ra tiếng Việt |
| **B2 – Permission matrix** | MANAGER vào KITCHEN, STAFF ra khỏi setAvailability |
| **B2 – Image upload** | MANAGER được upload ảnh site + ảnh local (trước chỉ ADMIN) |
| **B3 – Takeaway pause** | MANAGER/ADMIN tạm ngưng/mở lại nhận đơn mang về theo chi nhánh |
| **B3 – KitchenService** | acceptingTakeaway tính cả cờ takeaway_paused |
| **B4 – Demo data** | V37 migration: 4 categories, 11 menu items, 10 bàn/chi nhánh, giờ mở cửa |
| **Audit log** | Ghi nhật ký bất đồng bộ cho STAFF/MANAGER actions |
| **Website CMS** | Gallery, trang giới thiệu chi nhánh, cài đặt site |
| **Đặt bàn** | Guest public + Staff POS, override capacity cho MANAGER+ |
| **SePay webhook** | Tự xác nhận thanh toán chuyển khoản |
| **Kitchen SSE** | `GET /kitchen/branches/{id}/events` gửi `queue-updated` sau commit; giới hạn role và branch scope, có keepalive |
| **Postman collection** | 140 operation khớp OpenAPI hiện tại; có local environment và luồng mẫu POS → thanh toán → bếp |
| **87 test pass** | JUnit + MockMvc integration test chạy với PostgreSQL tại `TEST_DB_URL` (1 test tải cao được skip mặc định) |

---

### 🚧 Dự định hoàn thành / Còn thiếu

| Hạng mục | Mô tả | Ưu tiên |
|---|---|---|
| **FE – Màn hình bếp** | Hiển thị queue bếp realtime, nút start/ready/served | Cao |
| **FE – POS gọi món** | Giao diện tạo order, thêm món, chuyển trạng thái | Cao |
| **FE – Thanh toán POS** | Chọn phương thức, hiển thị QR, xác nhận | Cao |
| **FE – Quản lý nhân viên** | CRUD staff theo chi nhánh | Trung bình |
| **FE – Đặt bàn POS** | Quản lý đặt bàn trong admin portal | Trung bình |
| **FE – Dashboard chi nhánh** | Doanh thu, đơn hàng, tải bếp theo ngày | Trung bình |
| **FE – Menu management** | ADMIN sửa thực đơn, MANAGER bật/tắt món | Thấp |
| **FE – Pause takeaway UI** | Nút tạm ngưng/mở lại mang về trong admin | Thấp |
| **Dọn dẹp** | Khôi phục check localhost-only cho quick-login demo accounts | Sau deploy |

---

## 5. Cấu trúc API chính

### Public (không cần đăng nhập)

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/public/branches` | Danh sách chi nhánh + giờ mở cửa + tải bếp |
| GET | `/public/branches/{id}/menu` | Thực đơn takeaway của chi nhánh |
| POST | `/public/takeaway-orders` | Đặt đơn mang về |
| GET | `/public/branches/{id}/reservations/slots` | Khung giờ còn trống |
| POST | `/public/branches/{id}/reservations` | Khách đặt bàn online |
| POST | `/public/tables/{tableId}/order` | Khách quét QR gọi món |

### Xác thực

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/auth/login` | Đăng nhập → JWT cookie |
| POST | `/auth/logout` | Xóa cookie |
| POST | `/auth/refresh` | Làm mới access token |

### Bếp (KITCHEN = ADMIN, MANAGER, CHEF)

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/kitchen/branches/{id}/queue` | Queue bếp hiện tại |
| PUT | `/kitchen/items/{id}/start` | Bắt đầu nấu |
| PUT | `/kitchen/items/{id}/ready` | Xong |
| PUT | `/kitchen/items/{id}/serve` | Đã mang ra |
| GET | `/kitchen/branches/{id}/load` | Tải bếp + ước tính chờ |
| GET | `/kitchen/branches/{id}/events` | SSE `queue-updated` khi hàng đợi thay đổi; yêu cầu KITCHEN role và đúng branch |

### Order & Thanh toán (FRONT_OF_HOUSE = ADMIN, MANAGER, STAFF)

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/orders` | Tạo order |
| PUT | `/orders/{id}/items` | Thêm/sửa món |
| PUT | `/orders/{id}/complete` | Hoàn tất gọi món |
| POST | `/payments/{orderId}` | Thanh toán |
| POST | `/payments/{orderId}/refund` | Hoàn tiền (MANAGEMENT) |

### Quản lý chi nhánh (MANAGEMENT = ADMIN, MANAGER)

| Method | Endpoint | Mô tả |
|---|---|---|
| GET/PUT | `/branches/{id}` | Xem/sửa chi nhánh |
| PUT | `/branches/{id}/takeaway/pause` | Tạm ngưng mang về |
| PUT | `/branches/{id}/takeaway/resume` | Mở lại mang về |
| GET/POST | `/staff/branch/{id}` | Nhân viên theo chi nhánh |
| POST/PUT | `/admin/website/hours` | Giờ mở cửa |

---

## 6. Stack kỹ thuật

| Thành phần | Công nghệ |
|---|---|
| Backend | Spring Boot 4.1.1, Java 17, Spring Security, JWT (cookie HttpOnly) |
| Database | PostgreSQL 17, Flyway migrations (V1–V37) |
| ORM | Spring Data JPA / Hibernate |
| Authentication | JWT trong cookie `savoris_access` (HttpOnly, SameSite) |
| Image upload | Cloudinary (production), local file (dev) |
| Thanh toán | VietQR + SePay webhook |
| Frontend | React 18, TypeScript, Vite |
| Deploy BE | Render (auto-deploy từ master) |
| Deploy FE | Vercel (auto-deploy từ xdat310/savoris-fe master) |
| Test | JUnit 5, Testcontainers (PostgreSQL), MockMvc |

---

## 7. Ghi chú quan trọng

1. **KHÔNG push trực tiếp lên master** khi chưa test và backup DB — Render auto-deploy + Flyway chạy migration ngay lập tức, không rollback được.
2. **Mật khẩu production** (`savoris123`) chỉ trong biến môi trường `BOOTSTRAP_PASSWORD` trên Render, không commit vào code.
3. **SecurityRulesTest** bị exclude khi chạy `mvn test` do cần local user `savoris` — đây là issue sẵn có từ trước, không phải regression.
4. **Test DB:** `TEST_DB_URL=jdbc:postgresql://localhost:5433/savoris?currentSchema=savoris_test` — port 5433 (không phải 5432). Dùng `TEST_DB_URL='jdbc:postgresql://localhost:5433/savoris?currentSchema=savoris_test' ./mvnw test` để chạy integration suite.
5. **Quick-login** — Trang đăng nhập FE (`/login`) hiện hiện sẵn danh sách tài khoản demo (admin, manager, staff…) để click vào là tự điền username/password, tiện cho việc test. Tính năng này đang bật trên **cả production** (`savoris-fe.vercel.app`), tức người lạ cũng thấy và đăng nhập được. Sau khi demo/báo cáo xong cần sửa code FE để chỉ hiện trên `localhost`, ẩn trên production.
