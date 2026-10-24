package com.savoris;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.AreaEntity;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.entity.MenuCategoryEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.AreaRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import com.savoris.repository.MenuCategoryRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.jwt.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dinner rush on the real PostgreSQL database through real HTTP + JWT.
 * Runs in its own schema "loadtest" (wiped and re-created by the Flyway migrations every run), which also proves
 * the migrations match the entities. Dev data in the public schema is not touched:
 *   ./mvnw test -Dtest=PeakLoadTest -Dloadtest=true
 */
@EnabledIfSystemProperty(named = "loadtest", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "logging.level.com.savoris=WARN",
    "logging.level.org.springframework.security=WARN",
    "savoris.demo-data.enabled=false",
    "spring.datasource.url=jdbc:postgresql://localhost:5432/savoris?currentSchema=loadtest",
    "spring.flyway.schemas=loadtest",
    "spring.flyway.clean-disabled=false"
})
class PeakLoadTest {

    private static final int BRANCHES = 9;
    private static final int ORDERS_PER_BRANCH = 80;
    private static final int CLIENTS = 60;          // cashiers, waiters and apps placing orders at the same time
    private static final int COOKS_PER_BRANCH = 2;
    private static final long COOK_MILLIS_PER_DISH = 40;  // kitchen slower than the incoming rush on purpose

    @LocalServerPort int port;
    @Autowired BranchRepository branchRepository;
    @Autowired MenuCategoryRepository menuCategoryRepository;
    @Autowired MenuItemRepository menuItemRepository;
    @Autowired StaffRepository staffRepository;
    @Autowired JwtService jwtService;
    @Autowired AreaRepository areaRepository;
    @Autowired DiningTableRepository diningTableRepository;

    private final HttpClient http = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(100)).build();

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) throws Exception {
        TestJwtKeys.register(registry);
    }

    @TestConfiguration
    static class FreshSchema {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    record Branch(BranchEntity entity, List<Integer> tableIds, String cashierToken, String chefToken) { }

    @Test
    void dinnerRushAcrossNineBranches() throws Exception {
        List<MenuItemEntity> menu = createMenu();
        List<Branch> branches = new ArrayList<>();
        for (int i = 1; i <= BRANCHES; i++) branches.add(createBranch(i));
        String ownerToken = token(staff(StaffRole.ADMIN, null, "owner"));

        List<Long> orderLatencies = Collections.synchronizedList(new ArrayList<>());
        List<Long> dashboardLatencies = Collections.synchronizedList(new ArrayList<>());
        Map<Integer, AtomicInteger> statusCounts = new ConcurrentHashMap<>();
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger maxOverloaded = new AtomicInteger();
        AtomicBoolean rushOver = new AtomicBoolean(false);

        // Kitchens: each cook repeatedly takes the oldest pending dish and cooks it
        ExecutorService kitchens = Executors.newFixedThreadPool(BRANCHES * COOKS_PER_BRANCH);
        AtomicBoolean kitchensOpen = new AtomicBoolean(true);
        for (Branch branch : branches) {
            for (int c = 0; c < COOKS_PER_BRANCH; c++) {
                kitchens.submit(() -> cook(branch, kitchensOpen, unexpected));
            }
        }

        // Owner keeps the chain dashboard open during the rush
        ExecutorService owner = Executors.newSingleThreadExecutor();
        owner.submit(() -> {
            while (!rushOver.get()) {
                long start = System.nanoTime();
                HttpResponse<String> res = send("GET", "/dashboard/chain", ownerToken, null);
                dashboardLatencies.add((System.nanoTime() - start) / 1_000_000);
                if (res.statusCode() != 200) unexpected.add("dashboard " + res.statusCode());
                else maxOverloaded.accumulateAndGet(JsonPath.<Integer>read(res.body(), "$.overloadedBranches"), Math::max);
                Thread.sleep(100);
            }
            return null;
        });

        // The rush: orders from every branch at once, 60% dine-in, 40% takeaway/delivery
        ExecutorService clients = Executors.newFixedThreadPool(CLIENTS);
        Random random = new Random(42);
        long rushStart = System.nanoTime();
        for (int n = 0; n < ORDERS_PER_BRANCH; n++) {
            for (Branch branch : branches) {
                String type = switch (random.nextInt(10)) {
                    case 0, 1, 2 -> "TAKEAWAY";
                    case 3 -> "DELIVERY";
                    default -> "DINE_IN";
                };
                String body = orderBody(branch, type, menu, random);
                clients.submit(() -> {
                    long start = System.nanoTime();
                    HttpResponse<String> res = send("POST", "/orders", branch.cashierToken(), body);
                    orderLatencies.add((System.nanoTime() - start) / 1_000_000);
                    statusCounts.computeIfAbsent(res.statusCode(), k -> new AtomicInteger()).incrementAndGet();
                    if (res.statusCode() == 201) codes.add(JsonPath.read(res.body(), "$.code"));
                    else if (res.statusCode() != 503) unexpected.add("order " + res.statusCode() + " " + res.body());
                    return null;
                });
            }
        }
        clients.shutdown();
        assertThat(clients.awaitTermination(5, TimeUnit.MINUTES)).isTrue();
        long rushMillis = (System.nanoTime() - rushStart) / 1_000_000;
        rushOver.set(true);
        owner.shutdown();
        owner.awaitTermination(1, TimeUnit.MINUTES);

        // Let the kitchens catch up, then check the books
        long drainStart = System.nanoTime();
        while (pendingPortions(ownerToken) > 0 && System.nanoTime() - drainStart < TimeUnit.MINUTES.toNanos(3)) {
            Thread.sleep(200);
        }
        kitchensOpen.set(false);
        kitchens.shutdown();
        kitchens.awaitTermination(1, TimeUnit.MINUTES);
        String finalDashboard = send("GET", "/dashboard/chain", ownerToken, null).body();

        int accepted = statusCounts.getOrDefault(201, new AtomicInteger()).get();
        int paused = statusCounts.getOrDefault(503, new AtomicInteger()).get();
        System.out.printf("""

            ===== DINNER RUSH: %d branches, %d orders, %d concurrent clients =====
            Rush duration          : %d ms  (%.0f orders/s)
            Orders accepted (201)  : %d
            Takeaway paused (503)  : %d   (kitchen full, guests at tables still served)
            Unexpected errors      : %d
            Order latency  p50/p95/p99/max : %d / %d / %d / %d ms
            Dashboard latency p50/p95/max  : %d / %d / %d ms  (%d calls)
            Most branches overloaded at once: %d
            Open orders after kitchens drained: %s, portions still pending: %s
            =====================================================================
            """,
            BRANCHES, BRANCHES * ORDERS_PER_BRANCH, CLIENTS,
            rushMillis, BRANCHES * ORDERS_PER_BRANCH * 1000.0 / rushMillis,
            accepted, paused, unexpected.size(),
            percentile(orderLatencies, 50), percentile(orderLatencies, 95), percentile(orderLatencies, 99), percentile(orderLatencies, 100),
            percentile(dashboardLatencies, 50), percentile(dashboardLatencies, 95), percentile(dashboardLatencies, 100), dashboardLatencies.size(),
            maxOverloaded.get(),
            JsonPath.read(finalDashboard, "$.openOrders"), pendingPortions(ownerToken));

        assertThat(unexpected).as("unexpected errors").isEmpty();
        assertThat(accepted + paused).isEqualTo(BRANCHES * ORDERS_PER_BRANCH);
        assertThat(new HashSet<>(codes)).as("order codes are unique").hasSize(codes.size());
        assertThat(paused).as("overload protection kicked in during the rush").isPositive();
        assertThat(maxOverloaded.get()).isPositive();
        assertThat(pendingPortions(ownerToken)).as("kitchens caught up after the rush").isZero();
    }

    // Like a real kitchen screen: read the queue, work through it, refresh when done (or every 0.5 s when idle)
    private void cook(Branch branch, AtomicBoolean open, List<String> unexpected) {
        String queuePath = "/kitchen/branches/" + branch.entity().getId() + "/queue";
        while (open.get()) {
            try {
                String queue = send("GET", queuePath, branch.chefToken(), null).body();
                List<Integer> pending = JsonPath.read(queue, "$[?(@.status == 'PENDING')].itemId");
                if (pending.isEmpty()) {
                    Thread.sleep(500);
                    continue;
                }
                for (int itemId : pending) {
                    if (!open.get()) return;
                    // Two cooks may go for the same dish; the loser gets 409 and simply takes the next one
                    int started = send("PUT", "/kitchen/items/" + itemId + "/start", branch.chefToken(), null).statusCode();
                    if (started == 409) continue;
                    if (started != 200) unexpected.add("start " + started);
                    Thread.sleep(COOK_MILLIS_PER_DISH);
                    int ready = send("PUT", "/kitchen/items/" + itemId + "/ready", branch.chefToken(), null).statusCode();
                    int served = send("PUT", "/kitchen/items/" + itemId + "/served", branch.chefToken(), null).statusCode();
                    if (ready != 200 || served != 200) unexpected.add("ready/served " + ready + "/" + served);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                unexpected.add("cook " + e);
            }
        }
    }

    private long pendingPortions(String ownerToken) throws Exception {
        String json = send("GET", "/dashboard/chain", ownerToken, null).body();
        List<Number> pending = JsonPath.read(json, "$.branches[*].kitchen.pendingPortions");
        List<Number> cooking = JsonPath.read(json, "$.branches[*].kitchen.cookingPortions");
        return pending.stream().mapToLong(Number::longValue).sum() + cooking.stream().mapToLong(Number::longValue).sum();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String orderBody(Branch branch, String type, List<MenuItemEntity> menu, Random random) {
        StringBuilder items = new StringBuilder();
        int dishes = 1 + random.nextInt(4);
        for (int i = 0; i < dishes; i++) {
            if (i > 0) items.append(',');
            items.append("{\"menuItemId\":").append(menu.get(random.nextInt(menu.size())).getId())
                .append(",\"quantity\":").append(1 + random.nextInt(2)).append('}');
        }
        return "{\"branchId\":" + branch.entity().getId() + ",\"orderType\":\"" + type + "\""
            + ("DINE_IN".equals(type) ? ",\"tableId\":" + branch.tableIds().get(random.nextInt(branch.tableIds().size())) : "")
            + ",\"items\":[" + items + "]}";
    }

    private static long percentile(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        if (sorted.isEmpty()) return 0;
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
    }

    private List<MenuItemEntity> createMenu() {
        MenuCategoryEntity category = new MenuCategoryEntity();
        category.setName("Mon chinh");
        category = menuCategoryRepository.save(category);
        List<MenuItemEntity> menu = new ArrayList<>();
        String[] names = {"Pho bo", "Bun cha", "Com rang", "Nem ran", "Bun bo Hue", "Banh xeo", "Goi cuon", "Lau thai", "Tra da", "Che"};
        for (int i = 0; i < names.length; i++) {
            MenuItemEntity item = new MenuItemEntity();
            item.setCategoryId(category.getId());
            item.setCode("LT" + i);
            item.setName(names[i]);
            item.setBasePrice(BigDecimal.valueOf(30000 + i * 5000L));
            menu.add(menuItemRepository.save(item));
        }
        return menu;
    }

    private Branch createBranch(int i) {
        BranchEntity branch = new BranchEntity();
        branch.setCode("CN" + i);
        branch.setName("Chi nhanh " + i);
        branch.setAddress(i + " Le Loi");
        branch.setCity("Ha Noi");
        branch.setStatus(BranchStatus.ACTIVE);
        branch = branchRepository.save(branch);
        AreaEntity area = new AreaEntity();
        area.setBranchId(branch.getId());
        area.setName("Tang 1");
        area = areaRepository.save(area);
        List<Integer> tableIds = new ArrayList<>();
        for (int t = 1; t <= 20; t++) {
            DiningTableEntity table = new DiningTableEntity();
            table.setAreaId(area.getId());
            table.setName("T" + t);
            tableIds.add(diningTableRepository.save(table).getId());
        }
        return new Branch(branch, tableIds,
            token(staff(StaffRole.STAFF, branch, "cashier" + i)),
            token(staff(StaffRole.CHEF, branch, "chef" + i)));
    }

    private StaffEntity staff(StaffRole role, BranchEntity branch, String username) {
        StaffEntity staff = new StaffEntity();
        staff.setBranchId(branch != null ? branch.getId() : null);
        staff.setCode(username.toUpperCase());
        staff.setFullName(username);
        staff.setUsername(username);
        staff.setPasswordHash("not-used");
        staff.setRole(role);
        staff.setStatus(StaffStatus.ACTIVE);
        return staffRepository.save(staff);
    }

    private String token(StaffEntity staff) {
        return jwtService.generateAccessToken(staff);
    }
}
