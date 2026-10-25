package com.savoris.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.constant.StaffRole;
import com.savoris.entity.StaffEntity;
import com.savoris.security.BranchAccessGuard;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class WebsiteCmsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BranchAccessGuard branchAccess;

    private static final Map<String, String> TABLES = Map.of(
        "gallery", "gallery_image", "hours", "opening_hour"
    );

    @GetMapping("/public/site")
    public Map<String, Object> publicSite() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", singleton("site_setting"));
        result.put("branches", jdbc.queryForList("select id, code, name, slug, address, city, phone, email, latitude, longitude from branches where status='ACTIVE' order by id"));
        result.put("gallery", publicRows("gallery_image", null));
        return result;
    }

    @GetMapping("/public/branches/{slug}")
    public Map<String, Object> publicBranch(@PathVariable String slug) {
        Map<String, Object> branch = jdbc.query("select id, code, name, slug, address, city, phone, email, latitude, longitude from branches where slug=? and status='ACTIVE'", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : List.of("id", "code", "name", "slug", "address", "city", "phone", "email", "latitude", "longitude")) row.put(key, rs.getObject(key));
            return row;
        }, slug);
        if (branch == null) throw new AppException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh");
        Integer id = ((Number) branch.get("id")).intValue();
        Map<String, Object> page = jdbc.query("select payload::text from branch_page where branch_id=?", rs -> rs.next() ? jsonMap(rs.getString(1)) : Map.of(), id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("branch", branch);
        result.put("page", page);
        result.put("gallery", publicRows("gallery_image", id));
        result.put("openingHours", jdbc.queryForList("select weekday, opens_at, closes_at, closed from opening_hour where branch_id=? order by weekday", id));
        return result;
    }

    @GetMapping("/admin/website")
    @PreAuthorize(Roles.MANAGEMENT)
    public Map<String, Object> adminWebsite(@CurrentUser Integer staffId) {
        StaffEntity staff = branchAccess.currentUser(staffId);
        Map<String, Object> result = new LinkedHashMap<>();
        Integer branchId = staff.getRole() == StaffRole.MANAGER ? staff.getBranchId() : null;
        if (staff.getRole() == StaffRole.ADMIN) result.put("settings", singleton("site_setting"));
        String branchesSql = "select id, code, name, slug from branches where status='ACTIVE'" + (branchId == null ? "" : " and id=?") + " order by id";
        result.put("branches", branchId == null ? jdbc.queryForList(branchesSql) : jdbc.queryForList(branchesSql, branchId));
        for (String resource : TABLES.keySet()) result.put(resource, visibleRows(TABLES.get(resource), branchId));
        String pagesSql = "select branch_id, payload::text as payload from branch_page" + (branchId == null ? "" : " where branch_id=?");
        result.put("branchPages", branchId == null
            ? jdbc.query(pagesSql, (rs, n) -> Map.of("branchId", rs.getInt("branch_id"), "payload", jsonMap(rs.getString("payload"))))
            : jdbc.query(pagesSql, (rs, n) -> Map.of("branchId", rs.getInt("branch_id"), "payload", jsonMap(rs.getString("payload"))), branchId));
        return result;
    }

    @PutMapping("/admin/website/settings")
    @PreAuthorize(Roles.ADMIN)
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> payload) {
        String json = write(payload);
        jdbc.update("insert into site_setting(id,payload,updated_at) values(1,?::jsonb,now()) on conflict(id) do update set payload=excluded.payload, updated_at=now()", json);
        return payload;
    }

    @PutMapping("/admin/website/branches/{branchId}")
    @PreAuthorize(Roles.MANAGEMENT)
    public Map<String, Object> saveBranchPage(@PathVariable int branchId, @RequestBody Map<String, Object> payload, @CurrentUser Integer staffId) {
        branchAccess.check(staffId, branchId);
        String json = write(payload);
        int changed = jdbc.update("insert into branch_page(branch_id,payload,updated_at) values(?,?::jsonb,now()) on conflict(branch_id) do update set payload=excluded.payload, updated_at=now()", branchId, json);
        return payload;
    }

    @GetMapping("/admin/website/{resource}")
    @PreAuthorize(Roles.MANAGEMENT)
    public List<Map<String, Object>> adminList(@PathVariable String resource, @RequestParam(required = false) Integer branchId, @CurrentUser Integer staffId) {
        StaffEntity staff = branchAccess.currentUser(staffId);
        Integer scopedBranchId = requestedBranch(branchId, staff);
        return visibleRows(table(resource), scopedBranchId);
    }

    @PostMapping("/admin/website/{resource}")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<Map<String, Object>> create(@PathVariable String resource, @RequestBody Map<String, Object> payload, @CurrentUser Integer staffId) {
        String table = table(resource);
        payload = new LinkedHashMap<>(payload);
        StaffEntity staff = branchAccess.currentUser(staffId);
        if (staff.getRole() == StaffRole.MANAGER) {
            requestedBranch(branchId(payload), staff);
            payload.put("branchId", staff.getBranchId());
        }
        Map<String, Object> saved = persist(table, null, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/admin/website/{resource}/{id}")
    @PreAuthorize(Roles.MANAGEMENT)
    public Map<String, Object> update(@PathVariable String resource, @PathVariable long id, @RequestBody Map<String, Object> payload, @CurrentUser Integer staffId) {
        String table = table(resource);
        payload = new LinkedHashMap<>(payload);
        StaffEntity staff = branchAccess.currentUser(staffId);
        if (staff.getRole() == StaffRole.MANAGER) {
            checkRowBranch(table, id, staff);
            requestedBranch(branchId(payload), staff);
            payload.put("branchId", staff.getBranchId());
        }
        return persist(table, id, payload);
    }

    @PutMapping("/admin/website/{resource}/order")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<Void> reorder(@PathVariable String resource, @RequestBody List<Long> ids, @CurrentUser Integer staffId) {
        String table = table(resource);
        StaffEntity staff = branchAccess.currentUser(staffId);
        if (staff.getRole() == StaffRole.MANAGER) ids.forEach(id -> checkRowBranch(table, id, staff));
        for (int i = 0; i < ids.size(); i++) jdbc.update("update " + table + " set sort_order=? where id=?", i, ids.get(i));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/website/{resource}/{id}")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable long id, @CurrentUser Integer staffId) {
        String table = table(resource);
        StaffEntity staff = branchAccess.currentUser(staffId);
        if (staff.getRole() == StaffRole.MANAGER) checkRowBranch(table, id, staff);
        jdbc.update("delete from " + table + " where id=?", id);
        return ResponseEntity.noContent().build();
    }

    private String table(String resource) {
        String value = TABLES.get(resource);
        if (value == null) throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy mục CMS");
        return value;
    }

    @PutMapping("/admin/website/hours/week")
    @PreAuthorize(Roles.MANAGEMENT)
    @Transactional
    public ResponseEntity<List<Map<String, Object>>> saveWeekHours(@RequestBody Map<String, Object> body, @CurrentUser Integer staffId) {
        StaffEntity staff = branchAccess.currentUser(staffId);
        Object rawBranchId = pick(body, "branchId", "branch_id");
        if (rawBranchId == null) throw new AppException(ErrorCode.INVALID_REQUEST, "branchId là bắt buộc");
        int branchId = ((Number) rawBranchId).intValue();
        requestedBranch(branchId, staff);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) body.get("days");
        if (days == null || days.isEmpty()) throw new AppException(ErrorCode.INVALID_REQUEST, "days là bắt buộc và không được rỗng");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> day : days) {
            int weekday = extractWeekday(day);
            boolean closed = Boolean.TRUE.equals(day.getOrDefault("closed", false));
            LocalTime opensAt = parseTime(pick(day, "opensAt", "opens_at"), "opensAt");
            LocalTime closesAt = parseTime(pick(day, "closesAt", "closes_at"), "closesAt");
            if (!closed) {
                if (opensAt == null || closesAt == null) throw new AppException(ErrorCode.INVALID_REQUEST, "opensAt và closesAt là bắt buộc khi chi nhánh mở cửa");
                if (!opensAt.isBefore(closesAt)) throw new AppException(ErrorCode.INVALID_REQUEST, "opensAt phải trước closesAt");
            }
            Long savedId = jdbc.queryForObject(
                "insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,?,?,?) on conflict(branch_id,weekday) do update set opens_at=excluded.opens_at,closes_at=excluded.closes_at,closed=excluded.closed returning id",
                Long.class, branchId, weekday, opensAt, closesAt, closed);
            result.add(jdbc.queryForMap("select * from opening_hour where id=?", savedId));
        }
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> persist(String table, Long id, Map<String, Object> p) {
        String sql;
        Object[] args;
        if ("gallery_image".equals(table)) {
            sql = id == null ? "insert into gallery_image(branch_id,image_url,alt,sort_order,visible) values(?,?,?,?,?) returning id" : "update gallery_image set branch_id=?,image_url=?,alt=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), pick(p, "imageUrl", "image_url"), p.get("alt"), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else {
            int weekday = extractWeekday(p);
            boolean closed = Boolean.TRUE.equals(p.getOrDefault("closed", false));
            LocalTime opensAt = parseTime(pick(p, "opensAt", "opens_at"), "opensAt");
            LocalTime closesAt = parseTime(pick(p, "closesAt", "closes_at"), "closesAt");
            if (!closed) {
                if (opensAt == null || closesAt == null) throw new AppException(ErrorCode.INVALID_REQUEST, "opensAt và closesAt là bắt buộc khi chi nhánh mở cửa");
                if (!opensAt.isBefore(closesAt)) throw new AppException(ErrorCode.INVALID_REQUEST, "opensAt phải trước closesAt");
            }
            sql = id == null ? "insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,?,?,?) returning id" : "update opening_hour set branch_id=?,weekday=?,opens_at=?,closes_at=?,closed=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), weekday, opensAt, closesAt, closed};
        }
        if (id != null) args = Arrays.copyOf(args, args.length + 1);
        if (id != null) args[args.length - 1] = id;
        Long savedId = jdbc.queryForObject(sql, Long.class, args);
        return jdbc.queryForMap("select * from " + table + " where id=?", savedId);
    }

    private int extractWeekday(Map<String, Object> p) {
        Object raw = p.get("weekday");
        if (raw == null) throw new AppException(ErrorCode.INVALID_REQUEST, "weekday là bắt buộc");
        int weekday;
        try { weekday = ((Number) raw).intValue(); }
        catch (ClassCastException e) { throw new AppException(ErrorCode.INVALID_REQUEST, "weekday phải là số"); }
        if (weekday < 1 || weekday > 7) throw new AppException(ErrorCode.INVALID_REQUEST, "weekday phải từ 1 (Thứ Hai) đến 7 (Chủ Nhật)");
        return weekday;
    }

    private LocalTime parseTime(Object value, String fieldName) {
        if (value == null) return null;
        String s = value.toString().trim();
        try { return LocalTime.parse(s); }
        catch (Exception e) { throw new AppException(ErrorCode.INVALID_REQUEST, fieldName + " phải theo định dạng HH:mm"); }
    }

    private List<Map<String, Object>> visibleRows(String table, Integer branchId) {
        if (table.equals("site_setting")) return branchId == null ? List.of(singleton(table)) : List.of();
        if (table.equals("branch_page")) return List.of();
        String sql = "select * from " + table + (branchId == null ? "" : " where branch_id=?") + " order by " + (table.equals("opening_hour") ? "weekday" : "sort_order,id");
        return branchId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, branchId);
    }

    private Integer requestedBranch(Integer requestedBranchId, StaffEntity staff) {
        if (staff.getRole() == StaffRole.ADMIN) return requestedBranchId;
        branchAccess.check(staff, requestedBranchId == null ? staff.getBranchId() : requestedBranchId);
        if (requestedBranchId != null && !Objects.equals(requestedBranchId, staff.getBranchId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền truy cập chi nhánh này");
        }
        return staff.getBranchId();
    }

    private Integer branchId(Map<String, Object> payload) {
        Object value = pick(payload, "branchId", "branch_id");
        return value instanceof Number number ? number.intValue() : null;
    }

    private void checkRowBranch(String table, long id, StaffEntity staff) {
        Integer branchId = jdbc.query("select branch_id from " + table + " where id=?", rs -> rs.next() ? (Integer) rs.getObject(1) : null, id);
        if (branchId == null) throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền truy cập nội dung này");
        branchAccess.check(staff, branchId);
    }

    private List<Map<String, Object>> publicRows(String table, Integer branchId) {
        String sql = "select * from " + table + " where visible=true" + (branchId == null ? "" : " and branch_id=?") + " order by " + (table.equals("opening_hour") ? "weekday" : "sort_order,id");
        return branchId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, branchId);
    }

    private Map<String, Object> singleton(String table) {
        return jdbc.query("select payload::text from " + table + " where id=1", rs -> rs.next() ? jsonMap(rs.getString(1)) : Map.of());
    }

    private Map<String, Object> jsonMap(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception ex) { return Map.of(); }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new AppException(ErrorCode.INVALID_REQUEST, "Nội dung không hợp lệ"); }
    }

    private Object pick(Map<String, Object> values, String camel, String snake) {
        return values.containsKey(camel) ? values.get(camel) : values.get(snake);
    }

    private Object pick(Map<String, Object> values, String camel, String snake, Object fallback) {
        Object value = pick(values, camel, snake);
        return value == null ? fallback : value;
    }
}
