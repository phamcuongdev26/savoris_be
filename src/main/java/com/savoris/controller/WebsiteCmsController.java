package com.savoris.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class WebsiteCmsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static final Map<String, String> TABLES = Map.of(
        "offers", "offer", "gallery", "gallery_image", "accolades", "accolade",
        "testimonials", "testimonial", "menus", "menu_file", "hours", "opening_hour"
    );

    @GetMapping("/public/site")
    public Map<String, Object> publicSite() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", singleton("site_setting"));
        result.put("branches", jdbc.queryForList("select id, code, name, slug, address, city, phone, email, latitude, longitude from branches where status='ACTIVE' order by id"));
        result.put("gallery", publicRows("gallery_image", null));
        result.put("offers", publicRows("offer", null));
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
        result.put("offers", publicRows("offer", id));
        result.put("gallery", publicRows("gallery_image", id));
        result.put("accolades", publicRows("accolade", id));
        result.put("testimonials", publicRows("testimonial", id));
        result.put("menus", publicRows("menu_file", id));
        result.put("openingHours", jdbc.queryForList("select weekday, opens_at, closes_at, closed from opening_hour where branch_id=? order by weekday", id));
        return result;
    }

    @GetMapping("/admin/website")
    @PreAuthorize(Roles.ADMIN)
    public Map<String, Object> adminWebsite() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", singleton("site_setting"));
        result.put("branches", jdbc.queryForList("select id, code, name, slug from branches where status='ACTIVE' order by id"));
        for (String resource : TABLES.keySet()) result.put(resource, visibleRows(TABLES.get(resource), null));
        result.put("branchPages", jdbc.query("select branch_id, payload::text as payload from branch_page", (rs, n) -> Map.of("branchId", rs.getInt("branch_id"), "payload", jsonMap(rs.getString("payload")))));
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
    @PreAuthorize(Roles.ADMIN)
    public Map<String, Object> saveBranchPage(@PathVariable int branchId, @RequestBody Map<String, Object> payload) {
        String json = write(payload);
        int changed = jdbc.update("insert into branch_page(branch_id,payload,updated_at) values(?,?::jsonb,now()) on conflict(branch_id) do update set payload=excluded.payload, updated_at=now()", branchId, json);
        return payload;
    }

    @GetMapping("/admin/website/{resource}")
    @PreAuthorize(Roles.ADMIN)
    public List<Map<String, Object>> adminList(@PathVariable String resource, @RequestParam(required = false) Integer branchId) {
        return visibleRows(table(resource), branchId);
    }

    @PostMapping("/admin/website/{resource}")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Map<String, Object>> create(@PathVariable String resource, @RequestBody Map<String, Object> payload) {
        String table = table(resource);
        validatePrice(resource, payload);
        Map<String, Object> saved = persist(table, null, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/admin/website/{resource}/{id}")
    @PreAuthorize(Roles.ADMIN)
    public Map<String, Object> update(@PathVariable String resource, @PathVariable long id, @RequestBody Map<String, Object> payload) {
        validatePrice(resource, payload);
        return persist(table(resource), id, payload);
    }

    @PutMapping("/admin/website/{resource}/order")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> reorder(@PathVariable String resource, @RequestBody List<Long> ids) {
        String table = table(resource);
        for (int i = 0; i < ids.size(); i++) jdbc.update("update " + table + " set sort_order=? where id=?", i, ids.get(i));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/website/{resource}/{id}")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable long id) {
        jdbc.update("delete from " + table(resource) + " where id=?", id);
        return ResponseEntity.noContent().build();
    }

    private void validatePrice(String resource, Map<String, Object> payload) {
        if ("offers".equals(resource) && payload.get("price") instanceof Number n && n.longValue() < 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giá ưu đãi không được âm");
    }

    private String table(String resource) {
        String value = TABLES.get(resource);
        if (value == null) throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy mục CMS");
        return value;
    }

    private Map<String, Object> persist(String table, Long id, Map<String, Object> p) {
        String sql;
        Object[] args;
        if ("offer".equals(table)) {
            sql = id == null ? "insert into offer(branch_id,title,description,price,unit,schedule,image_url,image_alt,menu_url,sort_order,visible) values(?,?,?,?,?,?,?,?,?,?,?) returning id"
                : "update offer set branch_id=?,title=?,description=?,price=?,unit=?,schedule=?,image_url=?,image_alt=?,menu_url=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), p.get("title"), p.getOrDefault("description", ""), p.get("price"), p.getOrDefault("unit", "người"), p.getOrDefault("schedule", ""), pick(p, "imageUrl", "image_url"), pick(p, "imageAlt", "image_alt", "alt"), pick(p, "menuUrl", "menu_url"), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else if ("gallery_image".equals(table)) {
            sql = id == null ? "insert into gallery_image(branch_id,image_url,alt,sort_order,visible) values(?,?,?,?,?) returning id" : "update gallery_image set branch_id=?,image_url=?,alt=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), pick(p, "imageUrl", "image_url"), p.get("alt"), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else if ("accolade".equals(table)) {
            sql = id == null ? "insert into accolade(branch_id,award_year,content,sort_order,visible) values(?,?,?,?,?) returning id" : "update accolade set branch_id=?,award_year=?,content=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), pick(p, "year", "award_year"), p.get("content"), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else if ("testimonial".equals(table)) {
            sql = id == null ? "insert into testimonial(branch_id,quote,reviewer,title,sort_order,visible) values(?,?,?,?,?,?) returning id" : "update testimonial set branch_id=?,quote=?,reviewer=?,title=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), p.get("quote"), p.get("reviewer"), p.getOrDefault("title", ""), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else if ("menu_file".equals(table)) {
            sql = id == null ? "insert into menu_file(branch_id,title,file_url,sort_order,visible) values(?,?,?,?,?) returning id" : "update menu_file set branch_id=?,title=?,file_url=?,sort_order=?,visible=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), p.get("title"), pick(p, "fileUrl", "file_url"), pick(p, "sortOrder", "sort_order", 0), p.getOrDefault("visible", true)};
        } else {
            sql = id == null ? "insert into opening_hour(branch_id,weekday,opens_at,closes_at,closed) values(?,?,?,?,?) returning id" : "update opening_hour set branch_id=?,weekday=?,opens_at=?,closes_at=?,closed=? where id=? returning id";
            args = new Object[]{pick(p, "branchId", "branch_id"), p.get("weekday"), pick(p, "opensAt", "opens_at"), pick(p, "closesAt", "closes_at"), p.getOrDefault("closed", false)};
        }
        if (id != null) args = Arrays.copyOf(args, args.length + 1);
        if (id != null) args[args.length - 1] = id;
        Long savedId = jdbc.queryForObject(sql, Long.class, args);
        return jdbc.queryForMap("select * from " + table + " where id=?", savedId);
    }

    private List<Map<String, Object>> visibleRows(String table, Integer branchId) {
        if (table.equals("site_setting")) return List.of(singleton(table));
        if (table.equals("branch_page")) return List.of();
        String sql = "select * from " + table + (branchId == null ? "" : " where branch_id=?") + " order by " + (table.equals("opening_hour") ? "weekday" : "sort_order,id");
        return branchId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, branchId);
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
