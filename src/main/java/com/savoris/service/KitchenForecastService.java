package com.savoris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class KitchenForecastService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> RISKS = List.of("LOW", "NORMAL", "MEDIUM", "HIGH", "OVERLOADED");
    private static final String SYSTEM = "Bạn là chuyên gia phân tích vận hành bếp nhà hàng. Phân tích dữ liệu được cung cấp, "
        + "đưa ra khuyến nghị ngắn gọn và thực tế bằng tiếng Việt. Chỉ trả JSON đúng format yêu cầu, không giải thích thêm. "
        + "Không khẳng định số liệu ngoài dữ liệu đầu vào; active chefs là số tài khoản đầu bếp ACTIVE, không phải xác nhận ca làm.";

    private final AiModelClient ai;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final KitchenService kitchenService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public KitchenForecastResponse forecast(Integer staffId, Integer branchId) {
        if (!ai.isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        BranchLoadResponse load = kitchenService.computeLoad(branch);
        int chefs = (int) staffRepository.findByBranchIdAndStatus(branchId, StaffStatus.ACTIVE).stream()
            .filter(staff -> staff.getRole() == StaffRole.CHEF).count();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        int databaseToBusinessShiftMinutes = databaseToBusinessShiftMinutes();
        Map<java.time.LocalDate, List<HistoryRow>> historyByBusinessDate = new HashMap<>();
        List<HistoryRow> history = history(branchId, now, load.getKitchenCapacity(), databaseToBusinessShiftMinutes);
        historyByBusinessDate.put(now.toLocalDate(), history);
        LocalDateTime firstHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        List<HourStat> nextHours = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LocalDateTime hour = firstHour.plusHours(i);
            List<HistoryRow> sameWeekdayHistory = historyByBusinessDate.computeIfAbsent(hour.toLocalDate(),
                ignored -> history(branchId, hour, load.getKitchenCapacity(), databaseToBusinessShiftMinutes));
            String forecastWeekday = hour.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("vi-VN"));
            HourStat stat = sameWeekdayHistory.stream().filter(row -> row.hour == hour.getHour()).findFirst()
                .map(row -> new HourStat(hour, forecastWeekday, row.orders, row.portions, row.load))
                .orElse(new HourStat(hour, forecastWeekday, 0, 0, 0));
            nextHours.add(stat);
        }
        String userPrompt = "Chi nhánh: " + branch.getName() + "\nThời điểm hiện tại: " + now
            + "\nSức chứa bếp: " + load.getKitchenCapacity() + " món/giờ\nSố đầu bếp ACTIVE: " + chefs
            + " (số tài khoản, không phải xác nhận đang có mặt)\nĐang chờ nấu: " + load.getPendingPortions()
            + " món\nĐang nấu: " + load.getCookingPortions() + " món\nTải hiện tại: " + load.getLoadPercent()
            + "%\nLịch sử bốn tuần gần nhất theo đúng thứ của từng giờ dự báo, trung bình theo giờ: " + nextHours
            + "\nDự báo tải 2 giờ kế tiếp. risk chỉ thuộc LOW, NORMAL, MEDIUM, HIGH, OVERLOADED. predictedLoad là phần trăm. "
            + "Trả JSON: {\"forecast\":[{\"hour\":\"19h\",\"predictedLoad\":95,\"risk\":\"HIGH\"},"
            + "{\"hour\":\"20h\",\"predictedLoad\":110,\"risk\":\"OVERLOADED\"}],\"alert\":\"...\","
            + "\"actions\":[\"...\"],\"suggestedPauseAt\":null} .";
        AiModelClient.AiCompletion completion = ai.complete(List.of(
            Map.of("role", "system", "content", SYSTEM), Map.of("role", "user", "content", userPrompt)), List.of(), true);
        try {
            JsonNode root = mapper.readTree(completion.content());
            JsonNode forecasts = root.path("forecast");
            if (!forecasts.isArray() || forecasts.size() != 2) throw new IllegalArgumentException();
            List<KitchenForecastResponse.ForecastHour> output = new ArrayList<>();
            for (JsonNode item : forecasts) {
                String hour = bounded(item.path("hour").asText(), 12);
                JsonNode loadValue = item.path("predictedLoad");
                if (!loadValue.isIntegralNumber()) throw new IllegalArgumentException();
                int predictedLoad = loadValue.asInt(-1);
                String risk = item.path("risk").asText();
                if (hour.isBlank() || !hour.matches("(?:[01]?\\d|2[0-3])h") || predictedLoad < 0 || predictedLoad > 500 || !RISKS.contains(risk)) throw new IllegalArgumentException();
                output.add(new KitchenForecastResponse.ForecastHour(hour, predictedLoad, risk));
            }
            String alert = bounded(root.path("alert").asText(), 500);
            if (alert.isBlank()) throw new IllegalArgumentException();
            JsonNode actionsNode = root.path("actions");
            if (!actionsNode.isArray() || actionsNode.size() < 1 || actionsNode.size() > 2) throw new IllegalArgumentException();
            List<String> actions = new ArrayList<>();
            actionsNode.forEach(value -> {
                String action = bounded(value.asText(), 250);
                if (action.isBlank()) throw new IllegalArgumentException();
                actions.add(action);
            });
            String pause = root.path("suggestedPauseAt").isNull() ? null : bounded(root.path("suggestedPauseAt").asText(), 20);
            if (pause != null && !pause.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException();
            List<KitchenForecastResponse.HistoricalHour> historyOutput = history.stream().map(row ->
                new KitchenForecastResponse.HistoricalHour(String.format("%02dh", row.hour), row.orders, row.portions, row.load)).toList();
            return new KitchenForecastResponse(branchId, branch.getName(), load.getKitchenCapacity(), chefs,
                load.getPendingPortions(), load.getCookingPortions(), load.getLoadPercent(), historyOutput,
                output, alert, actions, pause, "Bảng lịch sử là trung bình bốn ngày cùng thứ với ngày hiện tại trong 28 ngày trước; giờ không có đơn được tính 0. "
                    + "Dữ liệu gửi model được lọc theo đúng thứ địa phương của từng giờ dự báo. Đầu bếp ACTIVE là tài khoản, không phải dữ liệu chấm công.");
        } catch (Exception ex) {
            if (ex instanceof AppException app) throw app;
            throw new AppException(ErrorCode.AI_PROVIDER_FAILED);
        }
    }

    private List<HistoryRow> history(Integer branchId, LocalDateTime businessTime, int capacity, int databaseToBusinessShiftMinutes) {
        LocalDateTime businessStart = businessTime.minusDays(28).toLocalDate().atStartOfDay();
        LocalDateTime businessEnd = businessTime.toLocalDate().atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            WITH local_orders AS (
              SELECT o.id, o.status AS order_status, oi.quantity, oi.needs_kitchen, oi.status AS item_status,
                     o.opened_at + (? * INTERVAL '1 minute') AS business_opened_at
              FROM orders o JOIN order_items oi ON oi.order_id = o.id
              WHERE o.branch_id = ? AND o.opened_at >= ? AND o.opened_at < ?
            ), daily AS (
              SELECT EXTRACT(HOUR FROM business_opened_at)::int AS hour,
                     DATE(business_opened_at) AS day,
                     COUNT(DISTINCT id)::double precision AS orders,
                     COALESCE(SUM(quantity), 0)::double precision AS portions
              FROM local_orders
              WHERE order_status NOT IN ('CANCELLED', 'AWAITING_PAYMENT')
                AND needs_kitchen = true AND item_status <> 'CANCELLED'
                AND EXTRACT(DOW FROM business_opened_at) = ?
              GROUP BY EXTRACT(HOUR FROM business_opened_at), DATE(business_opened_at)
            )
            SELECT hour, SUM(orders) / 4.0 AS average_orders, SUM(portions) / 4.0 AS average_portions
            FROM daily GROUP BY hour ORDER BY hour
            """, databaseToBusinessShiftMinutes, branchId,
                businessStart.minusMinutes(databaseToBusinessShiftMinutes),
                businessEnd.minusMinutes(databaseToBusinessShiftMinutes), businessTime.getDayOfWeek().getValue() % 7);
        Map<Integer, HistoryRow> rowsByHour = rows.stream().map(row -> {
            int hour = ((Number) row.get("hour")).intValue();
            double orders = ((Number) row.get("average_orders")).doubleValue();
            double portions = ((Number) row.get("average_portions")).doubleValue();
            double load = capacity <= 0 ? 0 : portions / capacity * 100;
            return new HistoryRow(hour, round(orders), round(portions), round(load));
        }).collect(java.util.stream.Collectors.toMap(HistoryRow::hour, row -> row));
        return IntStream.range(0, 24).mapToObj(hour -> rowsByHour.getOrDefault(hour, new HistoryRow(hour, 0, 0, 0))).toList();
    }
    private static int databaseToBusinessShiftMinutes() {
        Instant now = Instant.now();
        ZoneOffset databaseOffset = ZoneId.systemDefault().getRules().getOffset(now);
        ZoneOffset businessOffset = BUSINESS_ZONE.getRules().getOffset(now);
        return (businessOffset.getTotalSeconds() - databaseOffset.getTotalSeconds()) / 60;
    }
    private static String bounded(String value, int max) { if (value == null) throw new IllegalArgumentException(); String trimmed = value.trim(); if (trimmed.length() > max) throw new IllegalArgumentException(); return trimmed; }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
    private record HistoryRow(int hour, double orders, double portions, double load) {}
    private record HourStat(LocalDateTime at, String weekday, double orders, double portions, double load) {}
}
