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
        staff.deleteAll();
        branches.deleteAll();
        cn1 = branch("CN1"); cn2 = branch("CN2");
        admin = user("admin-http", StaffRole.ADMIN, null, StaffStatus.ACTIVE);
        manager = user("manager-http", StaffRole.MANAGER, cn1, StaffStatus.ACTIVE);
        manager2 = user("manager2-http", StaffRole.MANAGER, cn2, StaffStatus.ACTIVE);
        chef = user("chef-http", StaffRole.CHEF, cn1, StaffStatus.ACTIVE);
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
    void onlyAdminMayEditBranchAndChangePersists() throws Exception {
        String chefCookie = accessCookie(login("chef-http", "Correct@123"));
        String managerCookie = accessCookie(login("manager-http", "Correct@123"));
        String update = "{\"name\":\"Hoàn Kiếm cập nhật\",\"address\":\"Phố Huế\",\"city\":\"Hà Nội\",\"phone\":\"0900000000\",\"email\":\"cn1@savoris.vn\"}";
        assertEquals(403, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, chefCookie).statusCode());
        assertEquals(403, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, managerCookie).statusCode());
        String adminCookie = accessCookie(login("admin-http", "Correct@123"));
        assertEquals(200, exchange(HttpMethod.PUT, "/branches/" + cn1.getId(), update, adminCookie).statusCode());
        assertEquals("Hoàn Kiếm cập nhật", branches.findById(cn1.getId()).orElseThrow().getName());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return CLIENT.send(HttpRequest.newBuilder(URI.create(url("/auth/login")))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
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
        b.setAddress("Hà Nội"); b.setCity("Hà Nội"); b.setStatus(BranchStatus.ACTIVE); return branches.save(b);
    }

    private StaffEntity user(String username, StaffRole role, BranchEntity branch, StaffStatus status) {
        StaffEntity user = new StaffEntity(); user.setCode("U" + IDS.incrementAndGet()); user.setUsername(username);
        user.setEmail(username + "@savoris.vn"); user.setFullName(username); user.setPasswordHash(passwords.encode("Correct@123"));
        user.setRole(role); user.setBranchId(branch == null ? null : branch.getId()); user.setStatus(status); return staff.save(user);
    }

    private String url(String path) { return "http://localhost:" + port + "/api/v1" + path; }
}
