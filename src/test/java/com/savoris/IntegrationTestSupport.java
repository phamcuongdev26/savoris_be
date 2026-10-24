package com.savoris;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.AreaEntity;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.entity.BranchMenuItemEntity;
import com.savoris.entity.MenuCategoryEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.AreaRepository;
import com.savoris.repository.BranchMenuItemRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import com.savoris.repository.MenuCategoryRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every test builds its own branches, menu and staff, so tests never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
public abstract class IntegrationTestSupport {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected BranchRepository branchRepository;
    @Autowired protected MenuCategoryRepository menuCategoryRepository;
    @Autowired protected MenuItemRepository menuItemRepository;
    @Autowired protected BranchMenuItemRepository branchMenuItemRepository;
    @Autowired protected StaffRepository staffRepository;
    @Autowired protected AreaRepository areaRepository;
    @Autowired protected DiningTableRepository diningTableRepository;
    @Autowired protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) throws Exception {
        TestJwtKeys.register(registry);
    }

    protected static int unique() {
        return UNIQUE.incrementAndGet();
    }

    protected String takeawayPickupAt() {
        return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(2).withSecond(0).withNano(0).toString();
    }

    protected BranchEntity createBranch(Integer kitchenCapacity) {
        int n = unique();
        BranchEntity branch = new BranchEntity();
        branch.setCode("T" + n);
        branch.setName("Test branch " + n);
        branch.setAddress(n + " Test street");
        branch.setCity("Ha Noi");
        branch.setStatus(BranchStatus.ACTIVE);
        branch.setKitchenCapacity(kitchenCapacity);
        branch = branchRepository.save(branch);
        for (int weekday = 1; weekday <= 7; weekday++) {
            jdbcTemplate.update("insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,'00:00','23:59:59',false)", branch.getId(), weekday);
        }

        AreaEntity area = new AreaEntity();
        area.setBranchId(branch.getId());
        area.setName("Floor 1");
        area = areaRepository.save(area);
        for (int t = 1; t <= 5; t++) {
            DiningTableEntity table = new DiningTableEntity();
            table.setAreaId(area.getId());
            table.setName("T" + t);
            diningTableRepository.save(table);
        }
        return branch;
    }

    // n-th table (1-based) of a branch created by createBranch
    protected int table(BranchEntity branch, int n) {
        return diningTableRepository.findByBranchId(branch.getId()).get(n - 1).getId();
    }

    protected ResultActions pay(StaffEntity cashier, int orderId, long cash) throws Exception {
        return mockMvc.perform(post("/payments").with(as(cashier)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":" + orderId + ",\"cashReceived\":" + cash + "}"));
    }

    protected MenuItemEntity createMenuItem(String name, long price) {
        MenuCategoryEntity category = new MenuCategoryEntity();
        category.setName("Category " + unique());
        category = menuCategoryRepository.save(category);

        MenuItemEntity item = new MenuItemEntity();
        item.setCategoryId(category.getId());
        item.setCode("M" + unique());
        item.setName(name);
        item.setBasePrice(BigDecimal.valueOf(price));
        return menuItemRepository.save(item);
    }

    protected void overrideAtBranch(BranchEntity branch, MenuItemEntity item, Long price, boolean available) {
        BranchMenuItemEntity override = new BranchMenuItemEntity();
        override.setBranchId(branch.getId());
        override.setMenuItemId(item.getId());
        override.setPrice(price != null ? BigDecimal.valueOf(price) : null);
        override.setIsAvailable(available);
        branchMenuItemRepository.save(override);
    }

    protected StaffEntity createStaff(StaffRole role, BranchEntity branch) {
        int n = unique();
        StaffEntity staff = new StaffEntity();
        staff.setBranchId(branch != null ? branch.getId() : null);
        staff.setCode("S" + n);
        staff.setFullName("Staff " + n);
        staff.setUsername("staff" + n);
        staff.setPasswordHash("not-used");
        staff.setRole(role);
        staff.setStatus(StaffStatus.ACTIVE);
        return staffRepository.save(staff);
    }

    // Logs the request in as this staff member, the same way JwtAuthenticationFilter does
    protected static RequestPostProcessor as(StaffEntity staff) {
        return authentication(new UsernamePasswordAuthenticationToken(
            staff.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + staff.getRole().name()))));
    }

    protected static String orderJson(BranchEntity branch, String type, Integer tableId, int[]... menuItemAndQty) {
        StringBuilder items = new StringBuilder();
        for (int[] pair : menuItemAndQty) {
            if (items.length() > 0) items.append(',');
            items.append("{\"menuItemId\":").append(pair[0]).append(",\"quantity\":").append(pair[1]).append('}');
        }
        return "{\"branchId\":" + branch.getId() + ",\"orderType\":\"" + type + "\""
            + (tableId != null ? ",\"tableId\":" + tableId : "")
            + ",\"items\":[" + items + "]}";
    }

    protected static int[] dish(MenuItemEntity item, int quantity) {
        return new int[]{item.getId(), quantity};
    }
}
