package com.savoris;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class AuthBranchHttpTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final AtomicInteger IDS = new AtomicInteger();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=savoris_test");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("AUTH_COOKIE_SECURE", () -> "false");
        registry.add("AUTH_COOKIE_SAME_SITE", () -> "Lax");
        TestJwtKeys.register(registry);
    }

    @Autowired BranchRepository branches;
    @Autowired StaffRepository staff;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    private BranchEntity cn1;
    private BranchEntity cn2;
    private StaffEntity admin;
    private StaffEntity manager;
    private StaffEntity manager2;
    private StaffEntity chef;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        jdbc.update("delete from audit_logs");
        jdbc.update("update branches set manager_id=null");
        jdbc.update("delete from reservations");
        staff.deleteAll();
        jdbc.update("delete from branch_page");
        jdbc.update("delete from gallery_image");
        jdbc.update("delete from payments");
        jdbc.update("delete from order_items");
        jdbc.update("delete from orders");
        jdbc.update("delete from branch_menu_items");
        jdbc.update("delete from code_sequences");
        jdbc.update("delete from opening_hour");
        jdbc.update("delete from site_setting");
        branches.deleteAll();
        cn1 = branch("CN1"); cn2 = branch("CN2");
        admin = user("admin-http", StaffRole.ADMIN, null, StaffStatus.ACTIVE);
        manager = user("manager-http", StaffRole.MANAGER, cn1, StaffStatus.ACTIVE);
        manager2 = user("manager2-http", StaffRole.MANAGER, cn2, StaffStatus.ACTIVE);
        chef = user("chef-http", StaffRole.CHEF, cn1, StaffStatus.ACTIVE);
        cn1.setManagerId(manager.getId());
        cn1.setKitchenCapacity(7);
        cn1 = branches.save(cn1);
    }

    @Test
    void publicHealthAndBranchesWorkWithoutAuthentication() throws Exception {
        branch("CN3");
        branch("CN4");
        HttpResponse<String> health = exchange(HttpMethod.GET, "/health", null, null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("UP"));
        HttpResponse<String> response = exchange(HttpMethod.GET, "/public/branches", null, null);
        assertEquals(200, response.statusCode());
        assertEquals(4, response.body().split("\\\"code\\\"").length - 1);
        for (String code : List.of("CN1", "CN2", "CN3", "CN4")) assertTrue(response.body().contains(code));
        assertTrue(response.body().contains("\"phone\""));
        assertTrue(response.body().contains("\"email\""));
        assertTrue(response.body().contains("\"hours\""));
    }

    @Test
    void loginSetsHttpOnlyCookieAndRejectsBadOrLockedAccounts() throws Exception {
        HttpResponse<String> ok = login("admin-http", "Correct@123");
        assertEquals(200, ok.statusCode());
        List<String> cookies = ok.headers().allValues("set-cookie");
        assertTrue(cookies.stream().anyMatch(value -> value.startsWith("savoris_access=") && value.contains("HttpOnly")));
        assertTrue(cookies.stream().anyMatch(value -> value.startsWith("savoris_refresh=") && value.contains("HttpOnly")));
        assertFalse(ok.body().contains("password"));
        assertFalse(ok.body().contains("$2a$") || ok.body().contains("$2b$"));
        assertEquals(401, login("admin-http", "incorrect").statusCode());

        StaffEntity locked = user("locked-http", StaffRole.STAFF, cn1, StaffStatus.LOCKED);
        assertEquals(403, login(locked.getUsername(), "Correct@123").statusCode());
    }

    @Test
    void managerIsScopedAndCannotCreateElevatedRoles() throws Exception {
        String managerCookie = accessCookie(login("manager-http", "Correct@123"));
        assertEquals(401, exchange(HttpMethod.GET, "/staff/branch/" + cn1.getId(), null, null).statusCode());
        assertEquals(403, exchange(HttpMethod.GET, "/staff/branch/" + cn2.getId(), null, managerCookie).statusCode());
        assertEquals(403, createAs(managerCookie, "ADMIN", null).statusCode());
        assertEquals(403, createAs(managerCookie, "MANAGER", cn1.getId()).statusCode());

        String body = staffBody("STAFF", cn1.getId());
        assertEquals(201, exchange(HttpMethod.POST, "/staff", body, managerCookie).statusCode());
        assertEquals(403, exchange(HttpMethod.POST, "/staff", staffBody("STAFF", cn2.getId()), managerCookie).statusCode());
    }

    @Test
    void managersMayEditOwnBranchButNotAnotherBranch() throws Exception {
        String chefCookie = accessCookie(login("chef-http", "Correct@123"));
        String managerCookie = accessCookie(login("manager-http", "Correct@123"));
        String update = "{\"name\":\"Hoàn Kiếm cập nhật\",\"address\":\"Số 12 Phố Huế, Hoàn Kiếm\",\"city\":\"Hà Nội\",\"phone\":\"0900000000\",\"email\":\"cn1@savoris.vn\",\"managerId\":" + manager2.getId() + ",\"kitchenCapacity\":1}";
        assertEquals(403, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, chefCookie).statusCode());
        assertEquals(200, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, managerCookie).statusCode());
        BranchEntity updated = branches.findById(cn1.getId()).orElseThrow();
        assertEquals("Hoàn Kiếm cập nhật", updated.getName());
        assertEquals(manager.getId(), updated.getManagerId());
        assertEquals(7, updated.getKitchenCapacity());
        assertEquals(403, exchange(HttpMethod.PUT, "/branches/" + cn2.getId(), update, managerCookie).statusCode());
        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        assertEquals(200, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, adminCookie).statusCode());
    }

    @Test
    void websiteCmsIsAuthenticatedAndScopedForBranchManagers() throws Exception {
        assertEquals(401, exchange(HttpMethod.GET, "/admin/website", null, null).statusCode());
        String chefCookie = accessCookie(login("chef-http", "Correct@123"));
        assertEquals(403, exchange(HttpMethod.GET, "/admin/website", null, chefCookie).statusCode());

        String managerCookie = accessCookie(login("manager-http", "Correct@123"));
        HttpResponse<String> website = exchange(HttpMethod.GET, "/admin/website", null, managerCookie);
        assertEquals(200, website.statusCode());
        assertTrue(website.body().contains("\"code\":\"CN1"));
        assertFalse(website.body().contains("\"code\":\"CN2"));
        assertFalse(website.body().contains("\"settings\""));
        assertEquals(200, exchange(HttpMethod.PUT, "/admin/website/branches/" + cn1.getId(), "{\"introduction\":\"Bài giới thiệu\"}", managerCookie).statusCode());
        assertEquals(403, exchange(HttpMethod.PUT, "/admin/website/branches/" + cn2.getId(), "{\"introduction\":\"Không được sửa\"}", managerCookie).statusCode());
        assertEquals(201, exchange(HttpMethod.POST, "/admin/website/gallery", "{\"branchId\":" + cn1.getId() + ",\"imageUrl\":\"https://example.com/own.jpg\",\"alt\":\"CN1\"}", managerCookie).statusCode());
        assertEquals(403, exchange(HttpMethod.POST, "/admin/website/gallery", "{\"branchId\":" + cn2.getId() + ",\"imageUrl\":\"https://example.com/other.jpg\",\"alt\":\"CN2\"}", managerCookie).statusCode());
        assertEquals(403, exchange(HttpMethod.PUT, "/admin/website/settings", "{}", managerCookie).statusCode());
    }

    @Test
    void branchCreateRequiresPhoneAndEmail() throws Exception {
        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        String incomplete = "{\"code\":\"CN99\",\"name\":\"Ba Đình\",\"address\":\"Số 10 phố X, Ba Đình\",\"city\":\"Hà Nội\"}";
        assertEquals(400, exchange(HttpMethod.POST, "/branches", incomplete, adminCookie).statusCode());
        String shortAddress = "{\"code\":\"CN98\",\"name\":\"Ba Đình\",\"address\":\"Hà Nội\",\"city\":\"Hà Nội\",\"phone\":\"0900000000\",\"email\":\"bad-address@savoris.vn\"}";
        assertEquals(400, exchange(HttpMethod.POST, "/branches", shortAddress, adminCookie).statusCode());
    }

    @Test
    void takeawayAvailabilityUsesTheCurrentVietnameseWeekdayHours() throws Exception {
        int weekday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfWeek().getValue();
        jdbc.update("insert into opening_hour(branch_id,weekday,closed) values(?,?,true)", cn1.getId(), weekday);
        jdbc.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'00:00','23:59:59',false)", cn2.getId(), weekday);

        HttpResponse<String> response = exchange(HttpMethod.GET, "/public/branches", null, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"openNow\":false"));
        assertTrue(response.body().contains("\"openNow\":true"));
        assertTrue(response.body().contains("\"acceptingTakeaway\":false"));
        String order = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách\",\"guestPhone\":\"0912345678\",\"payMethod\":\"CASH\",\"pickupAt\":\"" + pickupAt() + "\",\"items\":[{\"menuItemId\":1,\"quantity\":1}]}";
        assertEquals(409, exchange(HttpMethod.POST, "/public/takeaway-orders", order, null).statusCode());
    }

    @Test
    void guestReservationSlotsUseBranchHoursAndAdminCanFilterAndMarkNoShow() throws Exception {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        int weekday = tomorrow.getDayOfWeek().getValue();
        jdbc.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'15:00','16:00',false)", cn1.getId(), weekday);

        HttpResponse<String> slots = exchange(HttpMethod.GET,
            "/reservations/guest/slots?branchId=" + cn1.getId() + "&date=" + tomorrow, null, null);
        assertEquals(200, slots.statusCode());
        assertTrue(slots.body().contains(tomorrow.atTime(15, 0).toString()));
        assertTrue(slots.body().contains(tomorrow.atTime(15, 30).toString()));
        assertFalse(slots.body().contains(tomorrow.atTime(16, 0).toString()));

        String invalid = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách demo\",\"guestPhone\":\"0912345678\",\"reservationDate\":\"" + tomorrow.atTime(16, 0) + "\",\"numberOfGuests\":2}";
        assertEquals(400, exchange(HttpMethod.POST, "/reservations/guest", invalid, null).statusCode());
        String valid = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách demo\",\"guestPhone\":\"0912345678\",\"reservationDate\":\"" + tomorrow.atTime(15, 30) + "\",\"numberOfGuests\":2}";
        HttpResponse<String> created = exchange(HttpMethod.POST, "/reservations/guest", valid, null);
        assertEquals(201, created.statusCode());
        int reservationId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asInt();

        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        HttpResponse<String> filtered = exchange(HttpMethod.GET,
            "/reservations/branch/" + cn1.getId() + "?date=" + tomorrow + "&status=PENDING", null, adminCookie);
        assertEquals(200, filtered.statusCode());
        assertTrue(filtered.body().contains("\"id\":" + reservationId));
        assertTrue(exchange(HttpMethod.PUT, "/reservations/" + reservationId + "/no-show", "{}", adminCookie).body().contains("NO_SHOW"));
        assertTrue(exchange(HttpMethod.GET,
            "/reservations/branch/" + cn1.getId() + "?date=" + tomorrow + "&status=NO_SHOW", null, adminCookie).body()
            .contains("\"id\":" + reservationId));
    }

    @Test
    void staffReservationValidationAndCapacityOverride() throws Exception {
        int uid = IDS.incrementAndGet();
        StaffEntity staffUser = user("staff-res-" + uid, StaffRole.STAFF, cn1, StaffStatus.ACTIVE);
        String staffCookie  = accessCookie(login("staff-res-" + uid, "Correct@123"));
        String managerCookie = accessCookie(login("manager-http", "Correct@123"));

        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        int weekday = tomorrow.getDayOfWeek().getValue();
        // Opening window: 15:00 – 15:30 (single slot)
        jdbc.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'15:00','15:30',false)",
            cn1.getId(), weekday);
        LocalDateTime slot = tomorrow.atTime(15, 0);
        String base = "\"branchId\":" + cn1.getId() + ",\"customerName\":\"T\",\"customerPhone\":\"0911111111\"";

        // 1. Past date → 400 (Bean Validation @Future)
        String past = "{" + base + ",\"reservationDate\":\"" + LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1) + "\",\"numberOfGuests\":1}";
        assertEquals(400, exchange(HttpMethod.POST, "/reservations", past, staffCookie).statusCode());

        // 2. Outside opening hours → 400
        String outside = "{" + base + ",\"reservationDate\":\"" + tomorrow.atTime(17, 0) + "\",\"numberOfGuests\":1}";
        assertEquals(400, exchange(HttpMethod.POST, "/reservations", outside, staffCookie).statusCode());

        // 3. Manager fills the slot (40 guests in one booking)
        String fillBody = "{" + base + ",\"reservationDate\":\"" + slot + "\",\"numberOfGuests\":40}";
        assertEquals(201, exchange(HttpMethod.POST, "/reservations", fillBody, managerCookie).statusCode());

        String fullSlot = "{" + base + ",\"reservationDate\":\"" + slot + "\",\"numberOfGuests\":1}";
        // 4. Staff đặt khung đầy → 409
        assertEquals(409, exchange(HttpMethod.POST, "/reservations", fullSlot, staffCookie).statusCode());

        // 5. Staff gửi overrideCapacity=true → 403
        String staffOverride = "{" + base + ",\"reservationDate\":\"" + slot + "\",\"numberOfGuests\":1,\"overrideCapacity\":true}";
        assertEquals(403, exchange(HttpMethod.POST, "/reservations", staffOverride, staffCookie).statusCode());

        // 6. Manager với overrideCapacity=true → 201
        String managerOverride = "{" + base + ",\"reservationDate\":\"" + slot + "\",\"numberOfGuests\":1,\"overrideCapacity\":true}";
        assertEquals(201, exchange(HttpMethod.POST, "/reservations", managerOverride, managerCookie).statusCode());
    }

    @Test
    void adminMenuItemsControlWhetherTheyAppearInTakeawayMenu() throws Exception {
        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        String categoryName = "Mang về " + IDS.incrementAndGet();
        HttpResponse<String> category = exchange(HttpMethod.POST, "/menu-categories",
            "{\"name\":\"" + categoryName + "\",\"sortOrder\":1}", adminCookie);
        assertEquals(201, category.statusCode());
        int categoryId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(category.body()).get("id").asInt();

        String itemBase = "\"categoryId\":" + categoryId + ",\"basePrice\":120000,\"unit\":\"phần\",\"isActive\":true";
        String code = "TA" + IDS.incrementAndGet();
        HttpResponse<String> takeawayItem = exchange(HttpMethod.POST, "/menu-items",
            "{\"code\":\"" + code + "\",\"name\":\"Món mang về\"," + itemBase + ",\"takeawayAvailable\":true}", adminCookie);
        assertEquals(201, takeawayItem.statusCode());
        int takeawayItemId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(takeawayItem.body()).get("id").asInt();
        assertEquals(200, exchange(HttpMethod.PUT, "/menu-items/" + takeawayItemId + "/branches",
            "{\"branchIds\":[" + cn1.getId() + "]}", adminCookie).statusCode());

        HttpResponse<String> dineInOnlyItem = exchange(HttpMethod.POST, "/menu-items",
            "{\"code\":\"" + code + "X\",\"name\":\"Món chỉ tại chỗ\"," + itemBase + ",\"takeawayAvailable\":false}", adminCookie);
        assertEquals(201, dineInOnlyItem.statusCode());

        HttpResponse<String> menu = exchange(HttpMethod.GET, "/public/branches/" + cn1.getId() + "/menu", null, null);
        assertEquals(200, menu.statusCode());
        assertTrue(menu.body().contains("Món mang về"));
        assertFalse(menu.body().contains("Món chỉ tại chỗ"));
        HttpResponse<String> otherBranchMenu = exchange(HttpMethod.GET, "/public/branches/" + cn2.getId() + "/menu", null, null);
        assertFalse(otherBranchMenu.body().contains("Món mang về"));
        assertEquals(200, exchange(HttpMethod.PUT, "/branches/" + cn1.getId() + "/menu/" + takeawayItemId + "/availability",
            "{\"available\":false}", adminCookie).statusCode());
        assertTrue(exchange(HttpMethod.GET, "/public/branches/" + cn1.getId() + "/menu", null, null).body()
            .contains("\"available\":false"));
        int dineInOnlyId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(dineInOnlyItem.body()).get("id").asInt();
        int weekday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfWeek().getValue();
        jdbc.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'00:00','23:59:59',false)", cn1.getId(), weekday);
        String order = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách\",\"guestPhone\":\"0912345678\",\"payMethod\":\"CASH\",\"pickupAt\":\"" + pickupAt() + "\",\"items\":[{\"menuItemId\":" + dineInOnlyId + ",\"quantity\":1}]}";
        HttpResponse<String> rejectedOrder = exchange(HttpMethod.POST, "/public/takeaway-orders", order, null);
        assertEquals(409, rejectedOrder.statusCode());
        assertTrue(rejectedOrder.body().contains("MENU_ITEM_UNAVAILABLE"));

        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        int tomorrowWeekday = tomorrow.getDayOfWeek().getValue();
        jdbc.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'15:00','16:00',false)", cn1.getId(), tomorrowWeekday);
        String invalidPickup = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách\",\"guestPhone\":\"0912345678\",\"payMethod\":\"CASH\",\"pickupAt\":\"" + tomorrow.atTime(17, 0) + "\",\"items\":[{\"menuItemId\":" + takeawayItemId + ",\"quantity\":1}]}";
        HttpResponse<String> outsideHours = exchange(HttpMethod.POST, "/public/takeaway-orders", invalidPickup, null);
        assertEquals(409, outsideHours.statusCode());
        assertTrue(outsideHours.body().contains("Giờ lấy món nằm ngoài giờ hoạt động"));

        assertEquals(200, exchange(HttpMethod.PUT, "/branches/" + cn1.getId() + "/menu/" + takeawayItemId + "/availability",
            "{\"available\":true}", adminCookie).statusCode());
        String validPickup = "{\"branchId\":" + cn1.getId() + ",\"guestName\":\"Khách\",\"guestPhone\":\"0912345678\",\"payMethod\":\"CASH\",\"pickupAt\":\"" + tomorrow.atTime(15, 30) + "\",\"items\":[{\"menuItemId\":" + takeawayItemId + ",\"quantity\":1}]}";
        assertEquals(201, exchange(HttpMethod.POST, "/public/takeaway-orders", validPickup, null).statusCode());
        assertTrue(exchange(HttpMethod.GET, "/dashboard/chain", null, adminCookie).body().contains("\"openOrders\":1"));
        // CASH takeaway waits outside the kitchen until the cashier collects payment
        String chefCookie = accessCookie(login("chef-http", "Correct@123"));
        HttpResponse<String> kitchenQueue = exchange(HttpMethod.GET, "/kitchen/branches/" + cn1.getId() + "/queue", null, chefCookie);
        assertEquals(200, kitchenQueue.statusCode());
        assertEquals("[]", kitchenQueue.body().trim());
    }

    @Test
    void openingHoursValidationAndUpsert() throws Exception {
        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        String managerCookie = accessCookie(login("manager-http", "Correct@123"));
        String manager2Cookie = accessCookie(login("manager2-http", "Correct@123"));

        // Valid save for cn1 (weekday 1, 08:00–22:00)
        String valid = "{\"branchId\":" + cn1.getId() + ",\"weekday\":1,\"opensAt\":\"08:00\",\"closesAt\":\"22:00\",\"closed\":false}";
        assertEquals(201, exchange(HttpMethod.POST, "/admin/website/hours", valid, adminCookie).statusCode());

        // Duplicate weekday → 409 (unique constraint branch_id+weekday)
        assertEquals(409, exchange(HttpMethod.POST, "/admin/website/hours", valid, adminCookie).statusCode());

        // Invalid time format → 400
        String badTime = "{\"branchId\":" + cn1.getId() + ",\"weekday\":2,\"opensAt\":\"25:00\",\"closesAt\":\"22:00\",\"closed\":false}";
        assertEquals(400, exchange(HttpMethod.POST, "/admin/website/hours", badTime, adminCookie).statusCode());

        // opensAt >= closesAt → 400
        String badRange = "{\"branchId\":" + cn1.getId() + ",\"weekday\":3,\"opensAt\":\"22:00\",\"closesAt\":\"08:00\",\"closed\":false}";
        assertEquals(400, exchange(HttpMethod.POST, "/admin/website/hours", badRange, adminCookie).statusCode());

        // MANAGER saves for their own branch → 201
        String ownBranch = "{\"branchId\":" + cn1.getId() + ",\"weekday\":4,\"opensAt\":\"09:00\",\"closesAt\":\"21:00\",\"closed\":false}";
        assertEquals(201, exchange(HttpMethod.POST, "/admin/website/hours", ownBranch, managerCookie).statusCode());

        // MANAGER saves for another branch → 403
        String otherBranch = "{\"branchId\":" + cn2.getId() + ",\"weekday\":5,\"opensAt\":\"09:00\",\"closesAt\":\"21:00\",\"closed\":false}";
        assertEquals(403, exchange(HttpMethod.POST, "/admin/website/hours", otherBranch, managerCookie).statusCode());

        // Week upsert: cn2 manager saves all 7 days in one call
        String week = "{\"branchId\":" + cn2.getId() + ",\"days\":["
            + "{\"weekday\":1,\"opensAt\":\"10:00\",\"closesAt\":\"20:00\",\"closed\":false},"
            + "{\"weekday\":2,\"closed\":true},"
            + "{\"weekday\":3,\"opensAt\":\"10:00\",\"closesAt\":\"20:00\",\"closed\":false}"
            + "]}";
        HttpResponse<String> weekResp = exchange(HttpMethod.PUT, "/admin/website/hours/week", week, manager2Cookie);
        assertEquals(200, weekResp.statusCode());
        // Upsert again (should update, not 409)
        assertEquals(200, exchange(HttpMethod.PUT, "/admin/website/hours/week", week, manager2Cookie).statusCode());

        // Week upsert for another branch → 403
        String weekOther = "{\"branchId\":" + cn1.getId() + ",\"days\":[{\"weekday\":6,\"closed\":true}]}";
        assertEquals(403, exchange(HttpMethod.PUT, "/admin/website/hours/week", weekOther, manager2Cookie).statusCode());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return CLIENT.send(HttpRequest.newBuilder(URI.create(url("/auth/login")))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String pickupAt() {
        return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(2).withSecond(0).withNano(0).toString();
    }

    private HttpResponse<String> createAs(String cookie, String role, Integer branchId) throws Exception {
        String body = staffBody(role, branchId);
        return exchange(HttpMethod.POST, "/staff", body, cookie);
    }

    private HttpResponse<String> exchange(HttpMethod method, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)));
        if (body != null) request.header("Content-Type", "application/json");
        if (cookie != null) request.header("Cookie", cookie);
        if (body == null) request.method(method.name(), HttpRequest.BodyPublishers.noBody());
        else request.method(method.name(), HttpRequest.BodyPublishers.ofString(body));
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String accessCookie(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie").stream()
            .filter(value -> value.startsWith("savoris_access="))
            .map(value -> value.substring(0, value.indexOf(';'))).findFirst().orElseThrow();
    }

    private String staffBody(String role, Integer branchId) {
        int id = IDS.incrementAndGet();
        return "{\"code\":\"HT" + id + "\",\"fullName\":\"HTTP Staff\",\"username\":\"http-staff" + id
            + "\",\"email\":\"http" + id + "@savoris.vn\",\"phone\":\"0900000000\",\"password\":\"Correct@123\",\"role\":\""
            + role + "\",\"branchId\":" + (branchId == null ? "null" : branchId) + ",\"hourlyRate\":30000}";
    }

    private BranchEntity branch(String code) {
        BranchEntity b = new BranchEntity(); b.setCode(code + IDS.incrementAndGet()); b.setName(code);
        b.setAddress("Số 1 phố Mẫu, Hoàn Kiếm"); b.setCity("Hà Nội"); b.setPhone("0900000000");
        b.setEmail("demo" + IDS.incrementAndGet() + "@example.invalid"); b.setStatus(BranchStatus.ACTIVE); return branches.save(b);
    }

    private StaffEntity user(String username, StaffRole role, BranchEntity branch, StaffStatus status) {
        StaffEntity user = new StaffEntity(); user.setCode("U" + IDS.incrementAndGet()); user.setUsername(username);
        user.setEmail(username + "@savoris.vn"); user.setFullName(username); user.setPasswordHash(passwords.encode("Correct@123"));
        user.setRole(role); user.setBranchId(branch == null ? null : branch.getId()); user.setStatus(status); return staff.save(user);
    }

    private String url(String path) { return "http://localhost:" + port + "/api/v1" + path; }
}
